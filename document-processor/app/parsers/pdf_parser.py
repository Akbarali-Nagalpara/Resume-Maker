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


def parse_pdf(data: bytes, filename: str) -> ProcessedDocument:
    checksum = hashlib.sha256(data).hexdigest()
    doc = fitz.open(stream=data, filetype="pdf")
    try:
        pages_info: list[dict] = []
        text_blocks: list[dict] = []
        fonts: dict[str, set[float]] = {}
        images: list[dict] = []
        tables_count = 0
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
        columns = _detect_columns(text_blocks)
        margins = _estimate_margins(text_blocks)

        doc_blocks = _to_doc_blocks([b["text"] for b in text_blocks])
        content, sections, anchors, mappings = _build_content(doc_blocks)
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
            documentType="PDF", content=content, template=template, sections=sections
        )
    finally:
        doc.close()


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


def _detect_columns(blocks: list[dict]) -> int:
    """Two columns when text blocks form two distinct x-bands with a wide gap."""
    if len(blocks) < 6:
        return 1
    xs = sorted(b["x"] for b in blocks)
    best_gap = 0.0
    for left, right in zip(xs, xs[1:]):
        gap = right - left
        if gap > best_gap:
            best_gap = gap
    return 2 if best_gap > 100 else 1


def _estimate_margins(blocks: list[dict]) -> dict[str, float]:
    if not blocks:
        return {"top": 72.0, "bottom": 72.0, "left": 72.0, "right": 72.0}
    left = max(0.0, min(b["x"] for b in blocks))
    top = max(0.0, min(b["y"] for b in blocks))
    return {"top": round(top, 1), "bottom": 72.0, "left": round(left, 1), "right": 72.0}


def _to_doc_blocks(lines: list[str]) -> list[dict]:
    """Group raw lines into heading/body blocks (blank-line separated)."""
    blocks: list[dict] = []
    title: str | None = None
    body: list[str] = []
    node_ids: list[str] = []
    is_heading = False
    seen_known = False
    seen_any = False
    counter = 0
    for raw in lines:
        line = (raw or "").strip()
        counter += 1
        node = f"l{counter}"
        if not line:
            if body or is_heading:
                blocks.append(
                    {"title": title, "lines": list(body), "nodeIds": list(node_ids),
                     "heading": is_heading}
                )
                title, body, node_ids, is_heading = None, [], [], False
            continue
        keyword = clf.classify_heading(line) is not None
        shaped = clf.looks_like_heading(line)
        heading = shaped and (keyword or seen_known)
        if heading and (seen_any or title is not None or body):
            if title is not None or body:
                blocks.append(
                    {"title": title, "lines": list(body), "nodeIds": list(node_ids),
                     "heading": is_heading}
                )
                body, node_ids = [], []
            title, node_ids, is_heading, seen_any = line, [node], True, True
            if keyword:
                seen_known = True
        else:
            body.append(line)
            node_ids.append(node)
            seen_any = True
    if title is not None or body:
        blocks.append(
            {"title": title, "lines": list(body), "nodeIds": list(node_ids),
             "heading": is_heading}
        )
    return blocks


def _first_heading(blocks: list[dict]) -> int:
    for i, block in enumerate(blocks):
        if block["heading"]:
            return i
    return len(blocks)


def _build_content(blocks: list[dict]):
    first = _first_heading(blocks)
    personal = _parse_personal([l for b in blocks[:first] for l in b["lines"]])
    sections: dict[str, dict] = {}
    titles: dict[str, str] = {}
    index: list[SectionIndex] = []
    anchors: list[SectionAnchor] = []
    mappings: dict[str, str] = {}
    position = 0
    for i in range(first, len(blocks)):
        block = blocks[i]
        if not block["heading"]:
            continue
        key = clf.classify_heading(block["title"] or "") or clf.slugify(block["title"] or "")
        section_lines = list(block["lines"])
        node_ids = [n for n in block["nodeIds"][1:] if n]
        for j in range(i + 1, len(blocks)):
            if blocks[j]["heading"]:
                break
            section_lines.extend(blocks[j]["lines"])
            node_ids.extend(blocks[j]["nodeIds"])
        sections[key] = {"lines": section_lines, "nodeIds": node_ids}
        titles[key] = block["title"] or key
        node_ref = ",".join(node_ids)
        mappings[key] = node_ref
        index.append(SectionIndex(key=key, title=block["title"] or key, position=position))
        anchors.append(
            SectionAnchor(key=key, title=block["title"] or key, position=position,
                          sourceNodeId=node_ref)
        )
        position += 1

    summary_lines = sections.get("summary", {}).get("lines", [])
    skills: list[str] = []
    for line in sections.get("skills", {}).get("lines", []):
        skills.extend(clf.split_skills(line))
    skills = list(dict.fromkeys(skills))
    experience = _parse_experience(sections.get("experience", {}).get("lines", []))
    projects = _parse_projects(sections.get("projects", {}).get("lines", []))
    education = _parse_education(sections.get("education", {}).get("lines", []))
    additional = [
        AdditionalSection(key=key, title=titles.get(key, key), paragraphs=body["lines"])
        for key, body in sections.items()
        if key not in KNOWN_CONTENT_KEYS
    ]
    mappings["personal.name"] = "l1"
    content = ResumeContent(
        personal=personal,
        summary="\n".join(summary_lines) or None,
        skills=skills,
        experience=experience,
        projects=projects,
        education=education,
        additionalSections=additional,
    )
    return content, index, anchors, mappings


def _parse_personal(lines: list[str]) -> PersonalInfo:
    name = lines[0] if lines else "Unnamed"
    info = PersonalInfo(name=name)
    title_set = False
    for line in lines[1:]:
        parts = clf.parse_contact_line(line)
        if "@" in line or parts.phone:
            if parts.email:
                info.email = parts.email
            if parts.phone:
                info.phone = parts.phone
            if parts.website:
                info.website = parts.website
            if parts.location:
                info.location = parts.location
        elif not title_set:
            info.title = line
            title_set = True
        elif info.location is None:
            info.location = line
    return info


def _group_experience(lines: list[str]) -> list[list[str]]:
    groups: list[list[str]] = []
    current: list[str] = []
    for line in lines:
        if not line.strip():
            continue
        role_line = " @ " in line
        next_dates = clf.contains_date_range(line) and any(clf.is_bullet(l) for l in current)
        if (role_line or next_dates) and current:
            groups.append(current)
            current = []
        current.append(line)
    if current:
        groups.append(current)
    return groups


def _to_experience(group: list[str], counter: int) -> ExperienceItem:
    heading = group[0]
    role, company = heading, None
    for sep in (" @ ", " — ", " – ", " - ", ", "):
        if sep in heading:
            role, company = (part.strip() for part in heading.split(sep, 1))
            break
    dates, bullets = None, []
    for line in group[1:]:
        if dates is None and clf.contains_date_range(line) and len(line) < 60:
            dates = line
        elif clf.is_bullet(line):
            bullets.append(clf.strip_bullet(line))
        elif line.strip():
            bullets.append(line.strip())
    return ExperienceItem(id=f"exp-{counter + 1}", role=role, company=company, dates=dates,
                          bullets=bullets)


def _parse_experience(lines: list[str]) -> list[ExperienceItem]:
    return [_to_experience(group, i) for i, group in enumerate(_group_experience(lines))]


def _group_blocks(lines: list[str]) -> list[list[str]]:
    groups: list[list[str]] = []
    current: list[str] = []
    seen_bullet = False
    for line in lines:
        bullet = clf.is_bullet(line)
        if not bullet and seen_bullet and current:
            groups.append(current)
            current, seen_bullet = [], False
        current.append(line)
        seen_bullet = seen_bullet or bullet
    if current:
        groups.append(current)
    return groups


def _parse_projects(lines: list[str]) -> list[ProjectItem]:
    items: list[ProjectItem] = []
    for i, group in enumerate(_group_blocks([l for l in lines if l.strip()])):
        if not group:
            continue
        stack, description = None, []
        for j in range(1, len(group)):
            line = clf.strip_bullet(group[j]) if clf.is_bullet(group[j]) else group[j]
            if j == len(group) - 1 and len(line) < 120 and any(s in line for s in ("·", "|", "/", ",")):
                stack = line
            else:
                description.append(line)
        items.append(ProjectItem(id=f"project-{i + 1}", name=group[0],
                                 description=" ".join(description) or None, stack=stack))
    return items


def _parse_education(lines: list[str]) -> list[EducationItem]:
    items: list[EducationItem] = []
    non_empty = [l for l in lines if l.strip()]
    if non_empty:
        items.append(EducationItem(
            id="education-1",
            degree=non_empty[0],
            school=non_empty[1] if len(non_empty) > 1 else None,
            dates=non_empty[-1] if len(non_empty) > 2 else None,
        ))
    return items


def to_dict(processed: ProcessedDocument) -> dict:
    return asdict(processed)
