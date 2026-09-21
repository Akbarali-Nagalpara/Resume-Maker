"""Regression: duplicate headings must yield unique deterministic keys.

Reproduces the reported failure where two identically-titled sections both
mapped to one key, violating uq_resume_sections_resume_key at persistence.
Blocks use the raw extractor shape (x/y/column) so the spatial pass runs.
"""

from app.parsers.pdf_parser import _build_content

_Y = [100]


def raw(text, x=72, bold_size=None, nid=None):
    _Y[0] += 14
    size = 11.0 if bold_size else 10.0
    font = "Helvetica-Bold" if bold_size else "Helvetica"
    return {
        "id": nid or f"t{_Y[0]}",
        "page": 1,
        "x": float(x),
        "y": float(_Y[0]),
        "width": 200.0,
        "height": 10.0,
        "origin": [float(x), float(_Y[0])],
        "font": font,
        "fontSize": size,
        "bold": bool(bold_size),
        "color": "#000000",
        "text": text,
        "column": 0,
    }


def _reset():
    _Y[0] = 100


def duplicate_blocks():
    _reset()
    return [
        raw("Maya Chen", nid="l1"),
        raw("WORK EXPERIENCE", bold_size=True, nid="l2"),
        raw("Senior Role @ Northstar", nid="l3"),
        raw("OPEN SOURCE", bold_size=True, nid="l4"),
        raw("Project Alpha.", nid="l5"),
        raw("WORK EXPERIENCE", bold_size=True, nid="l6"),
        raw("Junior Role @ Fieldwork", nid="l7"),
        raw("OPEN SOURCE", bold_size=True, nid="l8"),
        raw("Project Beta.", nid="l9"),
    ]


def test_duplicate_custom_headings_get_suffixed_keys():
    content, index, anchors, mappings = _build_content(duplicate_blocks())
    keys = [entry.key for entry in index]
    assert "custom-open-source" in keys
    assert "custom-open-source-2" in keys
    assert len(keys) == len(set(keys))
    custom = [s for s in content.additionalSections if s.key.startswith("custom-open-source")]
    assert len(custom) == 2
    assert custom[0].paragraphs == ["Project Alpha."]
    assert custom[1].paragraphs == ["Project Beta."]


def test_duplicate_known_headings_merge():
    content, index, anchors, mappings = _build_content(duplicate_blocks())
    experience_keys = [entry.key for entry in index if entry.key == "experience"]
    assert experience_keys == ["experience"]
    roles = [item.role for item in content.experience]
    assert roles == ["Senior Role", "Junior Role"]


def test_all_keys_unique_for_many_repeats():
    _reset()
    blocks = [raw("Name", nid="l0"), raw("EXPERIENCE", bold_size=True, nid="le")]
    for i in range(1, 5):
        blocks.append(raw("OPEN SOURCE", bold_size=True, nid=f"l{i}a"))
        blocks.append(raw(f"Detail {i}.", nid=f"l{i}b"))
    _, index, _, _ = _build_content(blocks)
    keys = [entry.key for entry in index]
    assert keys == [
        "experience",
        "custom-open-source",
        "custom-open-source-2",
        "custom-open-source-3",
        "custom-open-source-4",
    ]
