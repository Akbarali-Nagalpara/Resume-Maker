"""Surgical patch tests: only intended bytes change, the rest is preserved."""

import base64
import copy
import io
import json

import fitz
from fastapi.testclient import TestClient

from app.main import app
from app.parsers import pdf_parser
from tests.fixtures import make_pdf

client = TestClient(app)


def _parse(data: bytes) -> dict:
    parsed = pdf_parser.parse_pdf(data, "r.pdf")
    return json.loads(json.dumps(pdf_parser.to_dict(parsed)))


def _patch(data: bytes, old: dict, new: dict) -> dict:
    response = client.post(
        "/internal/patch/pdf",
        files={"file": ("r.pdf", io.BytesIO(data), "application/pdf")},
        data={"oldContent": json.dumps(old), "content": json.dumps(new)},
    )
    assert response.status_code == 200, response.text
    return response.json()


def _text(pdf_bytes: bytes) -> str:
    with fitz.open(stream=pdf_bytes, filetype="pdf") as doc:
        return "\n".join(page.get_text() for page in doc)


def test_no_edit_patches_nothing(tmp_path):
    pdf = make_pdf(str(tmp_path / "r.pdf"))
    with open(pdf, "rb") as fh:
        data = fh.read()
    old = _parse(data)["content"]
    body = _patch(data, old, copy.deepcopy(old))
    assert body["warnings"] == []
    assert body["patched"] == []


def test_name_change_only(tmp_path):
    pdf = make_pdf(str(tmp_path / "r.pdf"))
    with open(pdf, "rb") as fh:
        data = fh.read()
    old = _parse(data)["content"]
    new = copy.deepcopy(old)
    new["personal"]["name"] = "ANA RAE"
    body = _patch(data, old, new)
    assert body["warnings"] == []
    assert len(body["patched"]) == 1
    text = _text(base64.b64decode(body["pdfBase64"]))
    assert "ANA RAE" in text
    assert "Maya Chen" not in text
    for intact in ["Senior Product Engineer", "2022 - Present",
                   "TypeScript | React", "Led delivery"]:
        assert intact in text, intact


def test_overflow_is_reported_not_redesigned(tmp_path):
    pdf = make_pdf(str(tmp_path / "r.pdf"))
    with open(pdf, "rb") as fh:
        data = fh.read()
    old = _parse(data)["content"]
    new = copy.deepcopy(old)
    new["experience"][0]["bullets"] = ["Led delivery of a platform " + "very " * 80 + "long"]
    body = _patch(data, old, new)
    assert body["patched"] == []
    assert any(w["reason"] == "overflow" for w in body["warnings"])
    text = _text(base64.b64decode(body["pdfBase64"]))
    assert "Led delivery of a platform." in text


def test_added_lines_are_reported(tmp_path):
    pdf = make_pdf(str(tmp_path / "r.pdf"))
    with open(pdf, "rb") as fh:
        data = fh.read()
    old = _parse(data)["content"]
    new = copy.deepcopy(old)
    new["experience"].append({
        "id": "exp-99", "role": "Intern", "company": "Acme",
        "location": None, "dates": "2018", "bullets": ["Learned."],
    })
    body = _patch(data, old, new)
    assert any(w["reason"] == "unsupported-addition" for w in body["warnings"])
