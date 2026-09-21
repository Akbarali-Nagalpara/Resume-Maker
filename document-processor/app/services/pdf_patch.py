"""Surgical PDF patching (no LLM, no rebuild).

Strategy (capability-aware):
- Changed line-blocks whose font maps to a Base-14 family AND whose
  replacement fits the original width are patched in place: a white overlay
  covers only the old glyphs, then the new text is inserted at the original
  baseline origin with the original size/color. All other objects
  (images, vectors, lines, untouched text) are preserved byte-for-byte.
- Anything else (overflow, non-standard fonts, added lines, missing nodes)
  is reported as a warning and the original is left untouched.

The original PDF stays the authoritative visual source.
"""

from __future__ import annotations

import re

import fitz

from app.parsers import pdf_parser
from app.utils import classifier as clf

# PyMuPDF Base-14 families keyed by normalized PDF font names.
BASE14 = {
    "helvetica": "helv",
    "helvetica-bold": "hebo",
    "helvetica-oblique": "helo",
    "helvetica-boldoblique": "hebi",
    "times-roman": "tiro",
    "times-bold": "tibo",
    "times-italic": "tiit",
    "times-bolditalic": "tibi",
    "courier": "cour",
    "courier-bold": "cobo",
    "courier-oblique": "coit",
    "courier-boldoblique": "cobi",
    "symbol": "symb",
    "zapfdingbats": "zadg",
}

BULLET_MARKER_RE = re.compile(r"^(\u2022|-|\\*|o|▪|‣|▶|▸|→|⇒|\d+[.)])\s*")


# Metric-compatible twins for common non-Base-14 families (URW clones and
# Liberation families share Helvetica/Times/Courier metrics, so patching
# with the twin preserves layout geometry).
TWIN_FAMILIES = {
    # URW clones (note: "NimbusSanL", single-s) and Liberation families share
    # Helvetica/Times/Courier metrics, so patching with the twin preserves
    # layout geometry.
    "nimbussan": ("helv", "hebo"),
    "nimbusrom": ("tiro", "tibo"),
    "nimbusmon": ("cour", "cobo"),
    "liberationsans": ("helv", "hebo"),
    "liberationserif": ("tiro", "tibo"),
    "liberationmono": ("cour", "cobo"),
    "timesnewroman": ("tiro", "tibo"),
    "couriernew": ("cour", "cobo"),
}


def _normalize_family(font_name: str | None) -> str:
    if not font_name:
        return ""
    name = font_name.split("+")[-1].strip().lower().replace(" ", "")
    return name


def _base14_family(font_name: str | None) -> str | None:
    return BASE14.get(_normalize_family(font_name))


def _twin_family(font_name: str | None, bold: bool) -> str | None:
    """Metric-compatible Base-14 twin, or None when no safe twin exists."""
    name = _normalize_family(font_name)
    for prefix, (regular, bolded) in TWIN_FAMILIES.items():
        if name.startswith(prefix):
            return bolded if bold else regular
    return None


def _resolve_font(node: dict) -> tuple[str | None, bool]:
    """Returns (base14 family, substituted). substituted=True means a metric
    twin stands in for the original family (layout-safe, flagged)."""
    family = _base14_family(node.get("font"))
    if family is not None:
        return family, False
    twin = _twin_family(node.get("font"), bool(node.get("bold")))
    if twin is not None:
        return twin, True
    return None, False


def _hex_to_rgb(color: str | None) -> tuple[float, float, float]:
    if not color or not color.startswith("#"):
        return (0.0, 0.0, 0.0)
    try:
        value = int(color[1:], 16)
        return ((value >> 16 & 0xFF) / 255.0, (value >> 8 & 0xFF) / 255.0,
                (value & 0xFF) / 255.0)
    except ValueError:
        return (0.0, 0.0, 0.0)


def _restore_marker(old_text: str, new_text: str, force_bullet: bool = False) -> str:
    """Keeps list styling: re-applies the original bullet marker. With
    force_bullet (symbol-font lines), normalizes the marker to •."""
    if not new_text.strip() or clf.is_bullet(new_text):
        return new_text
    if force_bullet:
        return f"• {new_text.strip()}"
    match = BULLET_MARKER_RE.match((old_text or "").strip())
    if match:
        return f"{match.group(1)} {new_text.strip()}"
    return new_text


def _norm_line(text: str | None) -> str:
    """Comparison form: unmarked, stripped. Marker restoration happens at write."""
    stripped = (text or "").strip()
    if clf.is_bullet(stripped):
        return clf.strip_bullet(stripped)
    return stripped


def patch_pdf(
    data: bytes, old_content: dict, new_content: dict
) -> tuple[bytes, list[dict], list[str]]:
    """Patches changed fields in place using stable element references.
    Returns (pdf bytes, warnings, patched)."""
    layout = pdf_parser.extract_layout(data)
    nodes = {b["id"]: b for b in layout["blocks"]}

    # (nodeId, oldText, newText) pairs to attempt.
    pairs: list[tuple[str, str, str]] = []
    warnings: list[dict] = []

    parsed = pdf_parser.parse_pdf(data, "original.pdf")
    maps = parsed.template.mappings or {}

    def section_nodes(key: str) -> list[str]:
        return [n for n in (maps.get(key) or "").split(",") if n]

    def node_text(node_id: str) -> str:
        node = nodes.get(node_id)
        return node["text"] if node else ""

    def flow_fill(node_ids: list[str], new_text: str, key: str) -> None:
        """Word-wraps new text across the field's original nodes, measuring
        with each node's own (possibly substituted) font. Leftover words and
        unresolvable nodes produce warnings; originals are kept then."""
        words = new_text.split()
        if not words:
            for node_id in node_ids:
                old = node_text(node_id)
                if old.strip():
                    pairs.append((node_id, old, ""))
            return
        # Pre-resolve fonts; abort the field (keep originals) on any
        # unresolvable node instead of reflowing around it.
        resolved: list[tuple[str, dict, str]] = []
        for node_id in node_ids:
            node = nodes.get(node_id)
            if node is None:
                warnings.append({"nodeId": node_id, "reason": "missing-node",
                                 "detail": "Original element not found; skipped."})
                return
            family, substituted = _resolve_font(node)
            marker_fallback = False
            if family is None and clf.is_bullet(node.get("text") or ""):
                family, substituted, marker_fallback = "helv", True, True
            if family is None:
                warnings.append({
                    "nodeId": node_id, "reason": "unsupported-font",
                    "detail": _reason_detail("unsupported-font", node)})
                return
            resolved.append((node_id, node, family))
        remaining = list(words)
        assigned: list[tuple[str, dict, str, str]] = []
        for node_id, node, family in resolved:
            try:
                font = fitz.Font(fontname=family)
            except Exception:
                warnings.append({"nodeId": node_id, "reason": "unsupported-font",
                                 "detail": _reason_detail("unsupported-font", node)})
                return
            size = float(node.get("fontSize", 10))
            budget = float(node.get("width", 0)) + 2.0
            taken: list[str] = []
            while remaining:
                candidate = " ".join(taken + [remaining[0]])
                if font.text_length(candidate, fontsize=size) <= budget or not taken:
                    taken.append(remaining.pop(0))
                    if len(taken) == 1 and font.text_length(
                            taken[0], fontsize=size) > budget:
                        break
                else:
                    break
            assigned.append((node_id, node, family, " ".join(taken)))
        if remaining:
            warnings.append({
                "nodeId": None, "reason": "overflow",
                "detail": f"Text no longer fits '{key}'; {len(remaining)} word(s) "
                          "left over. Original kept for the unfilled part.",
            })
            return
        for node_id, node, family, text in assigned:
            old = node_text(node_id)
            marker = node.get("text") or ""
            final = _restore_marker(marker, text, "•" in marker or clf.is_bullet(marker))
            if _norm_line(old) == _norm_line(final) and old.strip() == final.strip():
                continue
            pairs.append((node_id, old, final))

    def zip_nodes(key: str, old_lines: list[str], node_ids: list[str],
                  new_lines: list[str]) -> None:
        for pos, node_id in enumerate(node_ids):
            old_text = old_lines[pos] if pos < len(old_lines) else ""
            new_text = new_lines[pos] if pos < len(new_lines) else ""
            if _norm_line(old_text) != _norm_line(new_text):
                pairs.append((node_id, old_text, new_text))
        if len(new_lines) > len(node_ids):
            warnings.append({
                "key": key, "nodeId": None, "reason": "unsupported-addition",
                "detail": f"{len(new_lines) - len(node_ids)} added line(s) have "
                          "no original element; layout unchanged for them.",
            })


    def propose(node_id: str, old_text: str, new_text: str) -> None:
        """Appends a raw pair; the jobs loop below is the single capability
        gate (and single warning point)."""
        if _norm_line(old_text) != _norm_line(new_text):
            pairs.append((node_id, old_text, new_text))

    def _patch_item_fields(kind: str, single_fields: tuple, old_items: list,
                           new_items: list, multi: dict | None = None) -> None:
        """Aligns item fields to their original elements: single-line fields
        patch in place, bullet lists zip positionally, descriptions flow-fill
        across their original nodes."""
        multi = multi or {}
        for idx, new_item in enumerate(new_items):
            if idx >= len(old_items):
                warnings.append({
                    "key": f"{kind}[{idx}]", "nodeId": None,
                    "reason": "unsupported-addition",
                    "detail": f"Added {kind} item has no original element; "
                              "layout unchanged for it.",
                })
                continue
            old_item = old_items[idx] or {}
            for field in single_fields:
                field_map = section_nodes(f"{kind}[{idx}].{field}")
                old_value = old_item.get(field)
                new_value = new_item.get(field)
                if (old_value or "") == (new_value or ""):
                    continue
                if not field_map:
                    if (new_value or "") != "":
                        warnings.append({
                            "key": f"{kind}[{idx}].{field}", "nodeId": None,
                            "reason": "unsupported-addition",
                            "detail": f"Added {kind} {field} has no original element; "
                                      "layout unchanged for it.",
                        })
                    continue
                propose(field_map[0], str(old_value or ""), str(new_value or ""))
                for extra in field_map[1:]:
                    propose(extra, node_text(extra), "")
            if kind == "experience":
                old_bullets = old_item.get("bullets") or []
                new_bullets = new_item.get("bullets") or []
                bullet_nodes = section_nodes(f"{kind}[{idx}].bullets")
                for pos, node_id in enumerate(bullet_nodes):
                    old_text = old_bullets[pos] if pos < len(old_bullets) else ""
                    new_text = new_bullets[pos] if pos < len(new_bullets) else ""
                    propose(node_id, str(old_text), str(new_text))
                if len(new_bullets) > len(bullet_nodes):
                    warnings.append({
                        "key": f"{kind}[{idx}]", "nodeId": None,
                        "reason": "unsupported-addition",
                        "detail": f"{len(new_bullets) - len(bullet_nodes)} added bullet(s) "
                                  "have no original element; layout unchanged for them.",
                    })
            if "description" in multi:
                desc_nodes = section_nodes(f"{kind}[{idx}].description")
                new_desc = str(new_item.get("description") or "")
                if desc_nodes or new_desc.strip():
                    if not desc_nodes:
                        warnings.append({
                            "key": f"{kind}[{idx}]", "nodeId": None,
                            "reason": "unsupported-addition",
                            "detail": "No original description element; layout unchanged.",
                        })
                    else:
                        flow_fill(desc_nodes, new_desc, f"{kind}[{idx}].description")

    # Personal name (single mapped node).
    old_personal = old_content.get("personal") or {}
    new_personal = new_content.get("personal") or {}
    old_name = old_personal.get("name") or ""
    new_name = new_personal.get("name") or ""
    name_nodes = section_nodes("personal.name")
    if name_nodes:
        propose(name_nodes[0], old_name, new_name)
    elif old_name.strip() != new_name.strip():
        warnings.append({"nodeId": None, "reason": "missing-node",
                         "detail": "Name element not found; skipped."})
    # Other contact fields have no stable element mapping in this layout:
    # report instead of silently dropping the edit.
    changed_contact = sorted(
        key for key in ("title", "email", "phone", "location", "website", "github",
                        "linkedin")
        if str(old_personal.get(key) or "") != str(new_personal.get(key) or "")
    )
    if changed_contact:
        warnings.append({
            "nodeId": None, "reason": "contact-preserved",
            "detail": f"Contact field(s) changed ({', '.join(changed_contact)}) but the "
                      "original contact line has no per-field mapping; it is preserved "
                      "as-is. The new values are saved and shown in the editor.",
        })
    # Summary (flow-fill across its nodes).
    summary_nodes = section_nodes("summary")
    new_summary = str(new_content.get("summary") or "")
    if summary_nodes or new_summary.strip():
        if not summary_nodes:
            warnings.append({
                "key": "summary", "nodeId": None, "reason": "unsupported-addition",
                "detail": "No original summary element; layout unchanged.",
            })
        else:
            flow_fill(summary_nodes, new_summary, "summary")
    # Skills: compare as value sets; on real change collapse into the
    # first node (mirrors the DOCX generator) and clear the rest. Labels
    # survive untouched while skills are unedited.
    skill_nodes = section_nodes("skills")
    new_skills = [str(s) for s in (new_content.get("skills") or [])]
    if skill_nodes or new_skills:
        if not skill_nodes:
            warnings.append({
                "key": "skills", "nodeId": None, "reason": "unsupported-addition",
                "detail": "No original skills element; layout unchanged.",
            })
        else:
            from app.parsers.pdf_parser import _split_skill_entries

            old_skill_lines = [node_text(n) for n in skill_nodes]
            if set(_split_skill_entries(old_skill_lines)) != set(new_skills):
                propose(skill_nodes[0],
                        old_skill_lines[0] if old_skill_lines else "",
                        " | ".join(new_skills))
                for extra in skill_nodes[1:]:
                    propose(extra, node_text(extra), "")

    # Item sections, field by field using stable element references.
    _patch_item_fields("experience", ("role", "company", "location", "dates"),
                       old_content.get("experience") or [],
                       new_content.get("experience") or [])
    _patch_item_fields("projects", ("name", "stack"),
                       old_content.get("projects") or [],
                       new_content.get("projects") or [],
                       multi={"description": True})
    _patch_item_fields("education", ("degree", "school", "dates"),
                       old_content.get("education") or [],
                       new_content.get("education") or [],
                       multi={})

    # Custom sections: positional line zip against their nodes.
    new_additional = {
        (a.get("key") or ""): [str(p) for p in (a.get("paragraphs") or [])]
        for a in (new_content.get("additionalSections") or [])
    }
    old_additional = {
        (a.get("key") or ""): [str(p) for p in (a.get("paragraphs") or [])]
        for a in (old_content.get("additionalSections") or [])
    }
    for key, new_lines in new_additional.items():
        key_nodes = section_nodes(key)
        if not key_nodes:
            if any(line.strip() for line in new_lines):
                warnings.append({
                    "key": key, "nodeId": None, "reason": "unsupported-addition",
                    "detail": "No original element for added content; layout unchanged.",
                })
            continue
        old_lines = old_additional.get(key)
        if old_lines is None:
            old_lines = [node_text(n) for n in key_nodes]
        zip_nodes(key, old_lines, key_nodes, new_lines)

    doc = fitz.open(stream=data, filetype="pdf")
    patched: list[str] = []
    try:
        # Resolve capability first so a single apply_redactions() call can
        # remove all old glyphs at once; insertions happen after.
        jobs: list[tuple[str, dict, str, str]] = []
        for node_id, old_text, new_text in pairs:
            node = nodes.get(node_id)
            if node is None:
                warnings.append({"nodeId": node_id, "reason": "missing-node",
                                 "detail": "Original element not found; skipped."})
                continue
            outcome, family, marker_fallback = _check_patchable(node, old_text, new_text)
            if outcome in ("ok", "font-substituted"):
                replacement = _restore_marker(
                    node.get("text") or "", new_text or "", marker_fallback)
                jobs.append((node_id, node, family, replacement))
                if outcome == "font-substituted":
                    warnings.append({
                        "nodeId": node_id, "reason": "font-substituted",
                        "detail": f"Font '{node.get('font')}' set in metric-compatible "
                                  f"'{family}; layout preserved.",
                    })
            else:
                warnings.append({"nodeId": node_id, "reason": outcome,
                                 "detail": _reason_detail(outcome, node)})
        by_page: dict[int, list] = {}
        for _, node, _, _ in jobs:
            by_page.setdefault(node.get("page", 1), []).append(node)
        for page_no, page_nodes in by_page.items():
            page = doc[page_no - 1]
            for node in page_nodes:
                page.add_redact_annot(
                    fitz.Rect(node["x"] - 1, node["y"] - 1,
                              node["x"] + node["width"] + 1,
                              node["y"] + node["height"] + 1)
                )
            page.apply_redactions()
        for node_id, node, family, replacement in jobs:
            _insert_replacement(doc[node.get("page", 1) - 1], node, family, replacement)
            patched.append(node_id)
        return doc.tobytes(), warnings, patched
    finally:
        doc.close()


def _reason_detail(reason: str, node: dict) -> str:
    if reason == "overflow":
        return (f"Replacement text is wider than the original element "
                f"({node['width']}pt); original kept.")
    if reason == "unsupported-font":
        return (f"Font '{node['font']}' cannot be reproduced exactly; "
                "original kept.")
    if reason == "font-substituted":
        return "Patched with a metric-compatible font twin; layout preserved."
    return "Original kept."


def _check_patchable(node: dict, old_text: str, new_text: str) -> tuple[str, str | None, bool]:
    """Returns (outcome, family, marker_fallback). Overflow compares against
    both the element width and the original advance (twins are close but not
    identical, so text as wide as the original must always be allowed)."""
    family, substituted = _resolve_font(node)
    marker_fallback = False
    if family is None and clf.is_bullet(node.get("text") or ""):
        # Symbol-font bullets (e.g. ▶ in MSAM): restyle the marker to • and
        # set the line in Helvetica.
        family, substituted, marker_fallback = "helv", True, True
    if family is None:
        return "unsupported-font", None, False
    size = float(node.get("fontSize", 10))
    replacement = _restore_marker(node.get("text") or "", new_text or "", marker_fallback)
    if not replacement.strip():
        return "ok", family, marker_fallback
    try:
        font = fitz.Font(fontname=family)
    except Exception:
        return "unsupported-font", None, False
    width = font.text_length(replacement, fontsize=size)
    old_advance = font.text_length(_restore_marker(
        node.get("text") or "", old_text or "", marker_fallback), fontsize=size)
    if width > float(node.get("width", 0)) + 2.0 and width > old_advance + 2.0:
        return "overflow", None, False
    return (("font-substituted", family, marker_fallback) if substituted else ("ok", family, marker_fallback))


def _insert_replacement(page, node: dict, family: str, replacement: str) -> None:
    if not replacement.strip():
        return
    size = float(node.get("fontSize", 10))
    origin = node.get("origin") or [node["x"], node["y"] + size]
    page.insert_text(
        fitz.Point(origin[0], origin[1]),
        replacement,
        fontname=family,
        fontsize=size,
        color=_hex_to_rgb(node.get("color")),
    )
