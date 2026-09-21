import io

from fastapi.testclient import TestClient

from app.main import app
from app.parsers import docx_parser, pdf_parser
from app.utils import detect
from tests.fixtures import make_docx, make_pdf

client = TestClient(app)


def test_detect_docx_and_pdf(tmp_path):
    docx = make_docx(str(tmp_path / "r.docx"))
    pdf = make_pdf(str(tmp_path / "r.pdf"))
    with open(docx, "rb") as fh:
        assert detect.detect("r.docx", fh.read(8)) == "DOCX"
    with open(pdf, "rb") as fh:
        assert detect.detect("r.pdf", fh.read(8)) == "PDF"


def test_detect_rejects_mismatch():
    try:
        detect.detect("r.pdf", b"PK\x03\x04xxxx")
    except detect.UnsupportedFormatError:
        pass
    else:
        raise AssertionError("expected UnsupportedFormatError")


def test_pdf_extraction(tmp_path):
    pdf = make_pdf(str(tmp_path / "r.pdf"))
    with open(pdf, "rb") as fh:
        data = fh.read()
    result = pdf_parser.parse_pdf(data, "r.pdf")
    assert result.documentType == "PDF"
    assert result.content.personal.name == "Maya Chen"
    assert "Product-minded" in (result.content.summary or "")
    assert len(result.content.skills) == 3
    assert result.content.experience[0].role == "Senior Product Engineer"
    assert result.template.page.widthPoints > 600
    assert result.template.properties["pageCount"] >= 1
    assert result.template.properties["fonts"]
    keys = [s.key for s in result.sections]
    assert keys == ["summary", "skills", "experience"]


def test_pdf_two_column_and_multipage(tmp_path):
    pdf = make_pdf(str(tmp_path / "two.pdf"), two_column=True, multi_page=True, image=True)
    with open(pdf, "rb") as fh:
        data = fh.read()
    result = pdf_parser.parse_pdf(data, "two.pdf")
    assert result.template.properties["columns"] == 2
    assert result.template.properties["pageCount"] == 2
    assert result.template.properties["imageCount"] >= 1


def test_docx_metadata(tmp_path):
    docx = make_docx(str(tmp_path / "r.docx"), two_column=True, with_table=True)
    with open(docx, "rb") as fh:
        data = fh.read()
    meta = docx_parser.parse_docx(data, "r.docx")
    assert meta["documentType"] == "DOCX"
    assert meta["columns"] == 2
    assert meta["tables"] == 1
    assert meta["paragraphCount"] > 5


def test_api_process_pdf(tmp_path):
    pdf = make_pdf(str(tmp_path / "r.pdf"))
    with open(pdf, "rb") as fh:
        response = client.post("/internal/process/pdf", files={"file": ("r.pdf", fh, "application/pdf")})
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["content"]["personal"]["name"] == "Maya Chen"
    assert body["template"]["documentType"] == "PDF"
    assert body["sourceChecksum"]
    # Shapes mirror the Spring records (camelCase, same field names).
    assert set(body["content"].keys()) == {
        "personal", "summary", "skills", "experience", "projects",
        "education", "additionalSections",
    }
    assert set(body["template"].keys()) == {
        "documentType", "sourceChecksum", "page", "sections", "mappings", "properties",
    }


def test_api_rejects_unsupported():
    response = client.post(
        "/internal/process/pdf",
        files={"file": ("notes.txt", io.BytesIO(b"hello"), "text/plain")},
    )
    assert response.status_code == 400


def test_api_health():
    assert client.get("/health").json()["status"] == "UP"
