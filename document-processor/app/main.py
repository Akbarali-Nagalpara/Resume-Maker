"""Document Processor: deterministic PDF/DOCX extraction (no LLM)."""

from fastapi import FastAPI

from app.api.routes import router

app = FastAPI(title="ResumeFlow Document Processor")
app.include_router(router)
