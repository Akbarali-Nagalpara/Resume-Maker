"""HTTP surface: health, format detection, PDF/DOCX processing."""

from __future__ import annotations

import hashlib

from fastapi import APIRouter, File, HTTPException, UploadFile

from app.parsers import docx_parser, pdf_parser
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
