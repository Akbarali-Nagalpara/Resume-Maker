"""HTTP surface: health, format detection, PDF/DOCX processing."""

from __future__ import annotations

import hashlib

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.parsers import docx_parser, pdf_parser
from app.services import pdf_patch
from app.utils import detect

router = APIRouter()


@router.get("/health")
def health() -> dict:
    return {"status": "UP", "service": "document-processor"}


@router.post("/internal/detect")
async def detect_format(file: UploadFile = File(...)) -> dict:
    head = await file.read(8)
    try:
        document_type = detect.detect(file.filename, head)
    except detect.UnsupportedFormatError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"documentType": document_type, "filename": file.filename}


@router.post("/internal/process/pdf")
async def process_pdf(file: UploadFile = File(...)) -> dict:
    data = await file.read()
    try:
        document_type = detect.detect(file.filename, data[:8])
    except detect.UnsupportedFormatError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    if document_type != "PDF":
        raise HTTPException(status_code=400, detail="not a PDF document")
    try:
        processed = pdf_parser.parse_pdf(data, file.filename or "resume.pdf")
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"cannot parse PDF: {exc}") from exc
    result = pdf_parser.to_dict(processed)
    result["sourceChecksum"] = hashlib.sha256(data).hexdigest()
    return result


@router.post("/internal/patch/pdf")
async def patch_pdf_endpoint(
    file: UploadFile = File(...),
    oldContent: str = Form(...),
    content: str = Form(...),
) -> dict:
    """Surgically patches changed lines in the original PDF.

    Form fields: file (original PDF), oldContent + content (ResumeContentModel
    JSON). Returns {pdfBase64, warnings[], patched[]}. Untouched objects are
    preserved; impossible edits are reported, never faked.
    """
    import base64
    import json

    data = await file.read()
    try:
        old_model = json.loads(oldContent)
        new_model = json.loads(content)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail=f"invalid content JSON: {exc}") from exc
    try:
        pdf_bytes, warnings, patched = pdf_patch.patch_pdf(data, old_model, new_model)
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"cannot patch PDF: {exc}") from exc
    return {
        "pdfBase64": base64.b64encode(pdf_bytes).decode("ascii"),
        "warnings": warnings,
        "patched": patched,
    }


@router.post("/internal/process/docx")
async def process_docx(file: UploadFile = File(...)) -> dict:
    data = await file.read()
    try:
        document_type = detect.detect(file.filename, data[:8])
    except detect.UnsupportedFormatError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    if document_type != "DOCX":
        raise HTTPException(status_code=400, detail="not a DOCX document")
    try:
        return docx_parser.parse_docx(data, file.filename or "resume.docx")
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"cannot parse DOCX: {exc}") from exc
