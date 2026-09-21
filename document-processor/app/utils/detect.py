"""Magic-byte format detection. Never trusts the filename alone."""

from __future__ import annotations

ZIP_MAGIC = b"\x50\x4B\x03\x04"
PDF_MAGIC = b"\x25\x50\x44\x46"

ALLOWED = {"docx", "pdf"}


class UnsupportedFormatError(ValueError):
    pass


def extension_of(filename: str | None) -> str:
    if not filename or "." not in filename:
        return ""
    return filename.rsplit(".", 1)[-1].lower()


def detect(filename: str | None, head: bytes) -> str:
    extension = extension_of(filename)
    is_zip = bytes(head[:4]) == ZIP_MAGIC
    is_pdf = bytes(head[:4]) == PDF_MAGIC
    if is_zip and extension in ("docx", "doc"):
        return "DOCX"
    if is_pdf and extension == "pdf":
        return "PDF"
    raise UnsupportedFormatError(
        f"expected a DOCX (OOXML) or PDF document, got file '{filename}'"
    )
