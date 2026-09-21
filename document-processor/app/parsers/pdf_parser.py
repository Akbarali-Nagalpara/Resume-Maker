"""PDF extraction + layout analysis with PyMuPDF (+ pdfplumber for tables).

Deterministic, no LLM. Produces content + template model shapes that mirror
the Spring Boot records so the backend consumes them without remapping.
"""

from __future__ import annotations

import hashlib
import io
from dataclasses import asdict

import fitz  # PyMuPDF

from app.models.schemas import (
    AdditionalSection,
    EducationItem,
    ExperienceItem,
    PageGeometry,
    PersonalInfo,
    ProcessedDocument,
    ProjectItem,
    ResumeContent,
    SectionAnchor,
    SectionIndex,
    TemplateModel,
)
from app.utils import classifier as clf

MAX_TEMPLATE_BLOCKS = 400
KNOWN_CONTENT_KEYS = {"summary", "skills", "experience", "projects", "education"}


def _color_hex(color_int: int | None) -> str | None:
    if color_int is None:
        return None
    try:
        return f"#{int(color_int) & 0xFFFFFF:06x}"
    except (TypeError, ValueError):
        return None


def _is_bold(flags: int) -> bool:
    return bool(flags & 2**4)


def extract_layout(data: bytes) -> dict:
    """Shared layout extraction used by parsing and patching.

    Returns pages, text blocks (with stable ids, bboxes, fonts, origins),
    fonts, images and table count. Deterministic for identical input.
    """
    doc = fitz.open(stream=data, filetype="pdf")
    try:
        pages_info: list[dict] = []
        text_blocks: list[dict] = []
        fonts: dict[str, set[float]] = {}
        images: list[dict] = []
        block_counter = 0

        for page_no in range(len(doc)):
            page = doc[page_no]
            rect = page.rect
            pages_info.append(
                {"number": page_no + 1, "width": rect.width, "height": rect.height}
            )
            page_dict = page.get_text("dict", sort=True)
            for block in page_dict.get("blocks", []):
                if block.get("type", 0) != 0:
                    continue
                lines = block.get("lines", [])
                for line in lines:
                    spans = [s for s in line.get("spans", []) if s.get("text", "").strip()]
                    if not spans:
                        continue
                    text = "".join(s["text"] for s in spans)
                    first = spans[0]
                    bbox = line.get("bbox", [0, 0, 0, 0])
                    font_name = str(first.get("font", ""))
                    size = float(first.get("size", 10))
                    fonts.setdefault(font_name, set()).add(round(size, 1))
                    text_blocks.append(
                        {
                            "id": f"pg{page_no + 1}b{block_counter}",
                            "page": page_no + 1,
                            "x": round(bbox[0], 1),
                            "y": round(bbox[1], 1),
                            "width": round(bbox[2] - bbox[0], 1),
                            "height": round(bbox[3] - bbox[1], 1),
                            "origin": [
                                round(float(first["origin"][0]), 1),
                                round(float(first["origin"][1]), 1),
                            ],
                            "font": font_name,
                            "fontSize": round(size, 1),
                            "bold": _is_bold(int(first.get("flags", 0))),
                            "color": _color_hex(first.get("color")),
                            "text": text.strip(),
                        }
                    )
                    block_counter += 1
            for img in page.get_images(full=True):
                try:
                    rects = page.get_image_rects(img[0])
                    bbox = rects[0] if rects else None
                except Exception:
                    bbox = None
                images.append(
                    {
                        "page": page_no + 1,
                        "x": round(bbox.x0, 1) if bbox else None,
                        "y": round(bbox.y0, 1) if bbox else None,
                        "width": round(bbox.width, 1) if bbox else None,
                        "height": round(bbox.height, 1) if bbox else None,
                    }
                )

        tables_count = _count_tables(data)
        first = pages_info[0] if pages_info else {"width": 595.0, "height": 842.0}
        page_width = float(first["width"])
        columns = _detect_columns(text_blocks, page_width)
        _assign_columns(text_blocks, page_width, columns)
        margins = _estimate_margins(text_blocks)
        return {
            "pages": pages_info,
            "blocks": text_blocks,
            "fonts": fonts,
            "images": images,
            "tables": tables_count,
            "columns": columns,
            "margins": margins,
            "pageWidth": page_width,
        }
    finally:
        doc.close()


def parse_pdf(data: bytes, filename: str) -> ProcessedDocument:
    checksum = hashlib.sha256(data).hexdigest()
    layout = extract_layout(data)
    pages_info = layout["pages"]
    text_blocks = layout["blocks"]
    fonts = layout["fonts"]
    images = layout["images"]
    tables_count = layout["tables"]
    columns = layout["columns"]
    margins = layout["margins"]
    first = pages_info[0] if pages_info else {"width": 595.0, "height": 842.0}
    content, index, anchors, mappings = _build_content(text_blocks)
    properties = {
        "pages": pages_info,
        "pageCount": len(pages_info),
        "fonts": sorted(
            f"{name}@{size}" for name, sizes in fonts.items() for size in sorted(sizes)
        ),
        "blocks": text_blocks[:MAX_TEMPLATE_BLOCKS],
        "sampledBlocks": min(len(text_blocks), MAX_TEMPLATE_BLOCKS),
        "images": images,
        "imageCount": len(images),
        "tables": tables_count,
        "columns": columns,
    }
    template = TemplateModel(
        documentType="PDF",
        sourceChecksum=checksum,
        page=PageGeometry(
            widthPoints=float(first["width"]),
            heightPoints=float(first["height"]),
            marginTopPoints=margins["top"],
            marginBottomPoints=margins["bottom"],
            marginLeftPoints=margins["left"],
            marginRightPoints=margins["right"],
        ),
        sections=anchors,
        mappings=mappings,
        properties=properties,
    )
    void_filename = filename
    return ProcessedDocument(
        documentType="PDF", content=content, template=template, sections=index
    )


def _count_tables(data: bytes) -> int:
    try:
        import pdfplumber

        count = 0
        with pdfplumber.open(io.BytesIO(data)) as pdf:
            for page in pdf.pages:
                try:
                    count += len(page.extract_tables() or [])
                except Exception:
                    continue
        return count
    except Exception:
        return 0


def _detect_columns(blocks: list[dict], page_width: float = 595.0) -> int:
    """Column count from narrow blocks only: wide/centered elements (name,
    contact lines) bridge the inter-column gap and must not vote."""
    narrow = [b for b in blocks if b["width"] < 0.5 * page_width]
    if len(narrow) < 6:
        return 1
    xs = sorted(b["x"] for b in narrow)
    best_gap = 0.0
    for left, right in zip(xs, xs[1:]):
        gap = right - left
        if gap > best_gap:
            best_gap = gap
    return 2 if best_gap > 80 else 1


def _estimate_margins(blocks: list[dict]) -> dict[str, float]:
    if not blocks:
        return {"top": 72.0, "bottom": 72.0, "left": 72.0, "right": 72.0}
    left = max(0.0, min(b["x"] for b in blocks))
    top = max(0.0, min(b["y"] for b in blocks))
    return {"top": round(top, 1), "bottom": 72.0, "left": round(left, 1), "right": 72.0}


def _assign_columns(blocks: list[dict], page_width: float, columns: int) -> None:
    """Assigns each block to a text flow by x-start proximity. Flows follow
    the column the text visually sits in; short centered headings therefore
    join the flow of their body instead of collapsing into column 0."""
    split = page_width / 2
    if columns == 2:
        starts = sorted(
            b["x"] for b in blocks if b["width"] < 0.5 * page_width
        )
        if len(starts) >= 6:
            best_gap, split_at = 0.0, split
            for left, right in zip(starts, starts[1:]):
                if right - left > best_gap:
                    best_gap, split_at = right - left, (left + right) / 2
            if best_gap > 40:
                split = split_at
    for block in blocks:
        if columns == 2 and block["x"] >= split:
            block["column"] = 1
        else:
            block["column"] = 0


def _spatial_sections(blocks: list[dict]):
    """Bands top-to-bottom, left-to-right within a band, with per-column
    section tracking. A heading updates only its own column, so alternating
    two-column layouts assign each element to the section it visually sits
    under. Returns (preamble_blocks, sections, titles, index, anchors,
    mappings) with node-aligned lines."""
    by_page: dict[int, list[dict]] = {}
    for block in blocks:
        by_page.setdefault(block.get("page", 1), []).append(block)
    sections: dict[str, dict] = {}
    titles: dict[str, str] = {}
    index: list[SectionIndex] = []
    anchors: list[SectionAnchor] = []
    mappings: dict[str, str] = {}
    key_counts: dict[str, int] = {}
    preamble: list[dict] = []
    current: dict[int, str] = {}
    prev_text: dict[int, str] = {}
    seen_any_heading = False
    position = 0

    def register(title: str) -> str:
        nonlocal position
        base = clf.classify_heading(title) or clf.slugify(title)
        if base in sections and base in KNOWN_CONTENT_KEYS:
            return base
        count = key_counts.get(base, 0) + 1
        key_counts[base] = count
        key = base if count == 1 else f"{base}-{count}"
        while key in sections:
            count += 1
            key_counts[base] = count
            key = f"{base}-{count}"
        sections[key] = {"lines": [], "nodeIds": []}
        titles[key] = title
        mappings[key] = ""
        index.append(SectionIndex(key=key, title=title, position=position))
        anchors.append(SectionAnchor(key=key, title=title, position=position,
                                     sourceNodeId=""))
        position += 1
        return key

    def append_body(key: str, text: str, node_id: str, size: float, col: int) -> None:
        sections[key]["lines"].append(text)
        sections[key]["nodeIds"].append(node_id)
        mappings[key] = ",".join(sections[key]["nodeIds"])
        for anchor in anchors:
            if anchor.key == key:
                anchor.sourceNodeId = mappings[key]
        prev_text[col] = text

    for page in sorted(by_page):
        rows = sorted(by_page[page], key=lambda b: b["y"])
        bands: list[list[dict]] = []
        for block in rows:
            if bands and block["y"] - bands[-1][0]["y"] <= 6.0:
                bands[-1].append(block)
            else:
                bands.append([block])
        prev_band_y: dict[int, float] = {}
        for band in bands:
            chips = len(band) >= 3
            band_y = band[0]["y"]
            # A large vertical gap within a column marks a paragraph break:
            # record it so item grouping can split here instead of guessing.
            for block in sorted(band, key=lambda b: b["x"]):
                col = block.get("column", 0)
                prev = prev_band_y.get(col)
                if prev is not None and band_y - prev > 24.0:
                    key = current.get(col)
                    if key is None:
                        preamble.append(
                            {"lines": [""], "nodeIds": [""], "sizes": [0.0],
                             "heading": False}
                        )
                    else:
                        append_body(key, "", "", 0.0, col)
                prev_band_y[col] = band_y
            for block in sorted(band, key=lambda b: b["x"]):
                text = (block["text"] or "").strip()
                if not text:
                    continue
                col = block.get("column", 0)
                node_id = block["id"]
                size = float(block.get("fontSize", 0.0))
                prev = prev_text.get(col, "")
                continuation = bool(prev) and (prev.endswith(",") or prev.endswith("-"))
                heading = (
                    not chips
                    and not continuation
                    and clf.is_section_heading(
                        text, styled=False,
                        seen_known=seen_any_heading or bool(sections),
                    )
                )
                if heading:
                    seen_any_heading = True
                    title = text
                    base = clf.classify_heading(title) or clf.slugify(title)
                    # Skill subcategory labels merge into the same-column
                    # skills section instead of splintering it.
                    if (
                        current.get(col) == "skills"
                        and title.endswith(":")
                        and len(title) < 30
                        and clf.classify_heading(title) in (None, "skills", "languages")
                    ):
                        append_body("skills", title, node_id, size, col)
                        continue
                    if base in sections and base in KNOWN_CONTENT_KEYS:
                        current[col] = base
                        continue
                    current[col] = register(title)
                else:
                    key = current.get(col)
                    if key is None:
                        preamble.append(
                            {"lines": [text], "nodeIds": [node_id],
                             "sizes": [size], "heading": False}
                        )
                    else:
                        append_body(key, text, node_id, size, col)
    return preamble, sections, titles, index, anchors, mappings


def _build_content(blocks: list[dict]):
    preamble, sections, titles, index, anchors, mappings = _spatial_sections(blocks)
    personal = _parse_personal(preamble)

    summary_lines = sections.get("summary", {}).get("lines", [])
    skills = _split_skill_entries(sections.get("skills", {}).get("lines", []))
    exp_body = sections.get("experience", {"lines": [], "nodeIds": []})
    experience, exp_nodes, exp_fields = _parse_experience(
        exp_body["lines"], exp_body["nodeIds"]
    )
    for i, nodes in enumerate(exp_nodes):
        mappings[f"experience[{i}]"] = ",".join(nodes)
    for i, fields in enumerate(exp_fields):
        for field, field_nodes in fields.items():
            mappings[f"experience[{i}].{field}"] = ",".join(
                node for node in field_nodes if node
            )
    proj_body = sections.get("projects", {"lines": [], "nodeIds": []})
    projects, proj_nodes, proj_fields = _parse_projects(
        proj_body["lines"], proj_body["nodeIds"]
    )
    for i, nodes in enumerate(proj_nodes):
        mappings[f"projects[{i}]"] = ",".join(nodes)
    for i, fields in enumerate(proj_fields):
        for field, field_nodes in fields.items():
            mappings[f"projects[{i}].{field}"] = ",".join(
                node for node in field_nodes if node
            )
    edu_body = sections.get("education", {"lines": [], "nodeIds": []})
    education, edu_nodes, edu_fields = _parse_education(
        edu_body["lines"], edu_body["nodeIds"]
    )
    for i, nodes in enumerate(edu_nodes):
        mappings[f"education[{i}]"] = ",".join(nodes)
    for i, fields in enumerate(edu_fields):
        for field, field_nodes in fields.items():
            mappings[f"education[{i}].{field}"] = ",".join(
                node for node in field_nodes if node
            )
    additional = [
        AdditionalSection(key=key, title=titles.get(key, key), paragraphs=body["lines"])
        for key, body in sections.items()
        if key not in KNOWN_CONTENT_KEYS
    ]
    mappings["personal.name"] = _preamble_name_node(preamble)
    content = ResumeContent(
        personal=personal,
        summary="\n".join(summary_lines).strip() or None,
        skills=skills,
        experience=experience,
        projects=projects,
        education=education,
        additionalSections=additional,
    )
    _sanitize(content)
    return content, index, anchors, mappings


def _looks_like_url_or_contact(value: str | None) -> bool:
    if not value:
        return False
    return bool(clf.URL_RE.search(value) or clf.EMAIL_RE.search(value)
                or "@" in value)


def _sanitize(content: ResumeContent) -> None:
    """Incorrect data is worse than missing data: clears fields holding
    obviously unrelated content (URLs/contacts in degree/school/location,
    contact data in location). Semantic misassignment is fixed at the
    section-assignment layer, not here."""
    personal = content.personal
    if personal.github and "github.com" not in personal.github.lower():
        personal.github = None
    if personal.linkedin and "linkedin.com" not in personal.linkedin.lower():
        personal.linkedin = None
    if personal.website and not clf.URL_RE.search(personal.website):
        personal.website = None
    if personal.location and (
        _looks_like_url_or_contact(personal.location)
        or clf.PHONE_RE.search(personal.location)
    ):
        rebuilt = clf.normalize_location(personal.location)
        personal.location = rebuilt
    for item in content.education:
        if _looks_like_url_or_contact(item.degree):
            item.degree = ""
        if _looks_like_url_or_contact(item.school):
            item.school = None
    content.skills = [s for s in content.skills if s.strip()]
    for item in content.experience:
        item.bullets = [b for b in item.bullets if b.strip()]


def _preamble_name_node(preamble: list[dict]) -> str:
    for block in preamble:
        if block["nodeIds"]:
            return block["nodeIds"][0]
    return ""


def _split_skill_entries(lines: list[str]) -> list[str]:
    """Subcategory labels stay verbatim; value lines split on separators."""
    skills: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped.endswith(":") and len(stripped) < 30:
            if stripped not in skills:
                skills.append(stripped)
        else:
            for skill in clf.split_skills(line):
                if skill not in skills:
                    skills.append(skill)
    return skills


def _parse_personal(blocks: list[dict]) -> PersonalInfo:
    """Name = largest type in the top preamble; contact lines split per-part
    into email/phone/github/linkedin/website/location; first remaining line
    becomes the professional title."""
    lines: list[str] = []
    sizes: list[float] = []
    for block in blocks:
        lines.extend(block["lines"])
        block_sizes = block.get("sizes", [])
        sizes.extend(block_sizes + [0.0] * (len(block["lines"]) - len(block_sizes)))
    name, name_index = "Unnamed", -1
    if lines:
        best, best_size = 0, -1.0
        for i, line in enumerate(lines):
            size = sizes[i] if i < len(sizes) else 0.0
            if line.strip() and (size, -i) > (best_size, -best):
                best, best_size = i, size
        name_index = best
        name = lines[best].strip()
    info = PersonalInfo(name=name)
    title_set = False
    for i, line in enumerate(lines):
        if i == name_index or not line.strip():
            continue
        parts = clf.parse_contact_line(line)
        if ("@" in line or parts.phone or parts.github or parts.linkedin or parts.website):
            if parts.email:
                info.email = parts.email
            if parts.phone:
                info.phone = parts.phone
            if parts.github:
                info.github = parts.github
            if parts.linkedin:
                info.linkedin = parts.linkedin
            if parts.website:
                info.website = parts.website
            if parts.location:
                info.location = (
                    f"{info.location}, {parts.location}" if info.location else parts.location
                )
        elif not title_set:
            info.title = line.strip()
            title_set = True
        elif info.location is None:
            info.location = line.strip()
    return info


def _group_experience(lines: list[str]) -> list[list[int]]:
    """Groups line indexes into items: new item at `role @ company`, or at a
    date-range line once the current group already holds bullets."""
    kept = [i for i, line in enumerate(lines) if line.strip()]
    groups: list[list[int]] = []
    current: list[int] = []
    for i in kept:
        line = lines[i]
        role_line = " @ " in line
        next_dates = clf.contains_date_range(line) and any(
            clf.is_bullet(lines[j]) for j in current
        )
        if (role_line or next_dates) and current:
            groups.append(current)
            current = []
        current.append(i)
    if current:
        groups.append(current)
    return groups


def _split_company_location(line: str) -> tuple[str | None, str | None]:
    """Splits 'Company • City, Country' style headers into parts."""
    for sep in ("•", "|", "·"):
        if sep in line:
            parts = [part.strip() for part in line.split(sep) if part.strip()]
            if len(parts) >= 2:
                return parts[0], ", ".join(parts[1:])
            if parts:
                return parts[0], None
    return line.strip() or None, None


def _is_contact_line(line: str) -> bool:
    parts = clf.parse_contact_line(line)
    return bool("@" in line or parts.phone or parts.github or parts.linkedin
                or parts.website)


def _to_experience(group: list[str], counter: int) -> tuple[ExperienceItem, dict]:
    """Returns (item, field->group-positions) so patching can target the
    original element per field instead of positional line zipping."""
    heading = group[0]
    role, company = heading, None
    # Only an explicit " @ " joins role and company on one line; em-dash
    # suffixes belong to the title and must not be split off.
    if " @ " in heading:
        role, company = (part.strip() for part in heading.split(" @ ", 1))
    fields: dict[str, list[int]] = {"role": [0]}
    if company is not None:
        fields["company"] = [0]
    dates, location, bullets = None, None, []
    bullet_pos: list[int] = []
    for pos in range(1, len(group)):
        line = group[pos]
        stripped = line.strip()
        if not stripped:
            continue
        if dates is None and clf.contains_date_range(stripped) and len(stripped) < 60:
            dates = stripped
            fields["dates"] = [pos]
        elif clf.is_bullet(line):
            bullets.append(clf.strip_bullet(line))
            bullet_pos.append(pos)
        elif _is_contact_line(stripped):
            continue
        elif ("•" in stripped or "|" in stripped or "·" in stripped) and company is None:
            company, location = _split_company_location(stripped)
            fields["company"] = [pos]
            if location:
                fields.setdefault("location", []).append(pos)
        elif company is None:
            company = stripped
            fields["company"] = [pos]
        elif location is None and "," in stripped and len(stripped) < 50:
            location = stripped
            fields["location"] = [pos]
        else:
            bullets.append(stripped)
            bullet_pos.append(pos)
    if company and clf.contains_date_range(company):
        dates, company = company, None
        fields["dates"] = fields.pop("company", [])
    if bullet_pos:
        fields["bullets"] = bullet_pos
    return (
        ExperienceItem(id=f"exp-{counter + 1}", role=role, company=company,
                       location=location, dates=dates, bullets=bullets),
        fields,
    )


def _parse_experience(
    lines: list[str], node_ids: list[str]
) -> tuple[list[ExperienceItem], list[list[str]], list[dict]]:
    items: list[ExperienceItem] = []
    groups_nodes: list[list[str]] = []
    groups_fields: list[dict] = []
    for group in _group_experience(lines):
        item, fields = _to_experience([lines[i] for i in group], len(items))
        items.append(item)
        item_nodes = [node_ids[i] if i < len(node_ids) else "" for i in group]
        groups_nodes.append(item_nodes)
        groups_fields.append(
            {field: [item_nodes[pos] for pos in positions]
             for field, positions in fields.items()}
        )
    return items, groups_nodes, groups_fields


_CONTINUATIONS = frozenset(
    [",", "-", "/", ":", "with", "and", "or", "the", "a", "an", "of", "to",
     "for", "in", "on", "by", "via", "as", "at", "from", "into", "using"]
)


def _continues(previous: str) -> bool:
    """Whether a line likely continues on the next line (ends with a
    preposition, conjunction or fragment punctuation)."""
    words = previous.strip().split()
    if not words:
        return False
    last = words[-1].strip("•·-,;:()\"'").lower()
    return not last or last in _CONTINUATIONS or previous.strip().endswith((",", "-", "/", ":"))


def _starts_item(line: str, seen_bullet: bool, has_current: bool, prev: str) -> bool:
    stripped = line.strip()
    return (
        not clf.is_bullet(line)
        and seen_bullet
        and has_current
        and len(stripped) < 80
        and not stripped.endswith((".", ",", ";", ":"))
        and bool(stripped)
        and stripped[0].isupper()
        and not _continues(prev)
    )


def _group_blocks(lines: list[str]) -> list[list[int]]:
    # Recorded paragraph breaks split deterministically; within a segment a
    # new item starts at a title-like line following bullets, so description
    # fragments never split items.
    segments: list[list[int]] = []
    current: list[int] = []
    for i, line in enumerate(lines):
        if not line.strip():
            if current:
                segments.append(current)
                current = []
            continue
        current.append(i)
    if current:
        segments.append(current)
    groups: list[list[int]] = []
    for segment in segments:
        sub: list[int] = []
        seen_bullet = False
        for i in segment:
            line = lines[i]
            bullet = clf.is_bullet(line)
            prev = lines[sub[-1]] if sub else ""
            if _starts_item(line, seen_bullet, bool(sub), prev):
                groups.append(sub)
                sub, seen_bullet = [], False
            sub.append(i)
            seen_bullet = seen_bullet or bullet
        if sub:
            groups.append(sub)
    return groups


def _is_stack_token(line: str) -> bool:
    token = line.strip()
    if not token or len(token) >= 40:
        return False
    import re as _re

    if token.isupper() or any(ch.isdigit() for ch in token):
        return True
    if _re.match(r"^[\w+-]+(\.[\w+-]+)+$", token):
        return True
    words = token.split()
    if len(words) == 1:
        return True
    return (
        len(words) <= 3
        and all(w[:1].isupper() or w.isupper() or any(c.isdigit() for c in w)
                for w in words)
    )


def _split_project_body(
    group: list[str],
) -> tuple[list[tuple[str, int]], str | None, list[int]]:
    """Stack = first run of 3+ stack-token lines anywhere after the name
    (chip layouts), else the legacy trailing separator line. Returns
    (description (line, group-pos) pairs, stack string, stack group positions).
    """
    body = list(enumerate(group[1:], start=1))
    run: list[int] = []
    best: list[int] | None = None
    for pos, _line in body:
        if _is_stack_token(_line):
            run.append(pos)
        else:
            if len(run) >= 3:
                best = run
                break
            run = []
    else:
        if len(run) >= 3:
            best = run
    if best is not None:
        chosen = set(best)
        stack = " · ".join(group[i] for i in best)
        return ([(group[i], i) for i, _ in body if i not in chosen], stack, best)
    if body:
        last_pos, last = body[-1]
        if len(last) < 120 and any(s in last for s in ("·", "|", "/", ",")):
            return ([(line, i) for i, line in body[:-1]], last, [last_pos])
    return ([(line, i) for i, line in body], None, [])


def _parse_projects(
    lines: list[str], node_ids: list[str]
) -> tuple[list[ProjectItem], list[list[str]], list[dict]]:
    items: list[ProjectItem] = []
    groups_nodes: list[list[str]] = []
    groups_fields: list[dict] = []
    for group in _group_blocks(lines):
        group_lines = [lines[i] for i in group]
        if not group_lines:
            continue
        description_pairs, stack, stack_positions = _split_project_body(group_lines)
        description = [
            clf.strip_bullet(line) if clf.is_bullet(line) else line
            for line, _ in description_pairs
        ]
        item_nodes = [node_ids[i] if i < len(node_ids) else "" for i in group]
        fields: dict[str, list[str]] = {"name": item_nodes[:1]}
        desc_nodes = [item_nodes[pos] for _, pos in description_pairs
                      if pos < len(item_nodes)]
        if any(desc_nodes):
            fields["description"] = desc_nodes
        stack_nodes = [item_nodes[pos] for pos in stack_positions
                       if pos < len(item_nodes)]
        if stack is not None and any(stack_nodes):
            fields["stack"] = stack_nodes
        items.append(
            ProjectItem(
                id=f"project-{len(items) + 1}",
                name=group_lines[0],
                description=" ".join(description) or None,
                stack=stack,
            )
        )
        groups_nodes.append(item_nodes)
        groups_fields.append(fields)
    return items, groups_nodes, groups_fields


def _parse_education(
    lines: list[str], node_ids: list[str]
) -> tuple[list[EducationItem], list[list[str]], list[dict]]:
    """One item per degree: a new item starts after a dates line when more
    content follows (degrees are date-terminated blocks)."""
    groups: list[list[int]] = []
    current: list[int] = []
    for i, line in enumerate(lines):
        if not line.strip():
            if current:
                groups.append(current)
                current = []
            continue
        if current and clf.contains_date_range(lines[current[-1]]):
            groups.append(current)
            current = []
        current.append(i)
    if current:
        groups.append(current)
    items: list[EducationItem] = []
    groups_nodes: list[list[str]] = []
    groups_fields: list[dict] = []
    for n, group in enumerate(groups):
        group_lines = [lines[i] for i in group]
        date_lines = [line for line in group_lines if clf.contains_date_range(line)]
        school_candidates = [
            line for line in group_lines[1:] if line not in date_lines and line.strip()
        ]
        item_nodes = [node_ids[i] if i < len(node_ids) else "" for i in group]
        fields: dict[str, list[str]] = {}
        if item_nodes[:1] != [""]:
            fields["degree"] = item_nodes[:1]
        # school = first non-date line after degree; dates = last date line.
        school_pos = next(
            (pos for pos in range(1, len(group_lines))
             if group_lines[pos] not in date_lines and group_lines[pos].strip()),
            None,
        )
        if school_pos is not None and school_pos < len(item_nodes):
            fields["school"] = [item_nodes[school_pos]]
        date_positions = [pos for pos, line in enumerate(group_lines) if line in date_lines]
        if date_positions and date_positions[-1] < len(item_nodes):
            fields["dates"] = [item_nodes[date_positions[-1]]]
        items.append(
            EducationItem(
                id=f"education-{n + 1}",
                degree=group_lines[0],
                school=school_candidates[0] if school_candidates else None,
                dates=date_lines[-1] if date_lines else None,
            )
        )
        groups_nodes.append(item_nodes)
        groups_fields.append(fields)
    return items, groups_nodes, groups_fields


def to_dict(processed: ProcessedDocument) -> dict:
    return asdict(processed)
