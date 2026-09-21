# ResumeFlow Document Processor

Deterministic document extraction for ResumeFlow. **No LLM.**

- PDF extraction + layout analysis: PyMuPDF (+ pdfplumber for tables)
- DOCX structural metadata: python-docx
- Stateless HTTP service; PostgreSQL stays owned by Spring Boot.

## Run

```sh
uv venv .venv
uv pip install --python .venv -r requirements.txt
DOC_PROCESSOR_PORT=8001 .venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Health: `GET /health`

## API

- `POST /internal/detect` (multipart `file`) → `{documentType, filename}`
- `POST /internal/process/pdf` (multipart `file`) → `{documentType, content, template, sections, sourceChecksum}`
- `POST /internal/process/docx` (multipart `file`) → structural metadata

The PDF response shapes mirror the Spring Boot `ResumeContentModel` /
`TemplateMetadata` records (camelCase) so the backend consumes them directly.

## Tests

```sh
.venv/bin/pytest
```
