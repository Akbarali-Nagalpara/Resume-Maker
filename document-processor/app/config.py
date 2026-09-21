"""Document Processor configuration. Environment only, no secrets in code."""

import os


def _get(name: str, default: str) -> str:
    return os.environ.get(name, default)


HOST = _get("DOC_PROCESSOR_HOST", "127.0.0.1")
PORT = int(_get("DOC_PROCESSOR_PORT", "8001"))
MAX_UPLOAD_MB = int(_get("DOC_PROCESSOR_MAX_UPLOAD_MB", "15"))
REQUEST_TIMEOUT_S = int(_get("DOC_PROCESSOR_TIMEOUT_S", "120"))
