"""Deterministic section/contact heuristics (no LLM).

Mirrors the Spring Boot SectionClassifier so both engines classify
identically. Extended with certifications, achievements, languages and
common heading variations ("WORK EXPERIENCE" -> experience, ...).
"""

from __future__ import annotations

import re
from dataclasses import dataclass

KEYWORDS: dict[str, str] = {
    "summary": "summary",
    "profile": "summary",
    "objective": "summary",
    "about": "summary",
    "skill": "skills",
    "technolog": "skills",
    "competenc": "skills",
    "experience": "experience",
    "employment": "experience",
    "work history": "experience",
    "work experience": "experience",
    "professional experience": "experience",
    "career history": "experience",
    "project": "projects",
    "education": "education",
    "qualification": "education",
    "academic": "education",
    "certification": "certifications",
    "certificate": "certifications",
    "license": "certifications",
    "achievement": "achievements",
    "accomplishment": "achievements",
    "award": "achievements",
    "honor": "achievements",
    "language": "languages",
    "publication": "publications",
    "course": "courses",
    "training": "courses",
    "interest": "interests",
    "hobb": "interests",
    "reference": "references",
    "volunteer": "volunteering",
}

EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")
PHONE_RE = re.compile(r"\+?[\d][\d\s().-]{6,}\d")
URL_RE = re.compile(r"(https?://[\w./-]+|[\w-]+\.[a-z]{2,}(?:/[\w./-]*)?)", re.IGNORECASE)
DATE_RANGE_RE = re.compile(
    r"(19|20)\d{2}\s*[—–\-to]+\s*((19|20)\d{2}|present|current)", re.IGNORECASE
)
BULLET_RE = re.compile(r"^[•\-\*o▪‣]\s+|^\d+[.)]\s+")


def classify_heading(heading: str | None) -> str | None:
    if not heading:
        return None
    normalized = heading.strip().lower()
    for keyword, key in KEYWORDS.items():
        if keyword in normalized:
            return key
    return None


def looks_like_heading(line: str | None) -> bool:
    if not line:
        return False
    text = line.strip()
    if not text or len(text) > 60 or text.endswith((".", ",")):
        return False
    if classify_heading(text):
        return True
    letters = [c for c in text if c.isalpha()]
    if len(letters) < 3:
        return False
    upper = sum(1 for c in letters if c.isupper())
    return (upper / len(letters)) > 0.7


def is_bullet(line: str | None) -> bool:
    return bool(line) and bool(BULLET_RE.match(line.strip()))


def strip_bullet(line: str) -> str:
    return BULLET_RE.sub("", line.strip()).strip()


def contains_date_range(line: str | None) -> bool:
    return bool(line) and bool(DATE_RANGE_RE.search(line))


@dataclass
class ContactParts:
    email: str | None = None
    phone: str | None = None
    website: str | None = None
    location: str | None = None


def _remove_first(pattern: re.Pattern, text: str) -> tuple[str | None, str]:
    match = pattern.search(text)
    if not match:
        return None, text
    found = match.group().strip()
    rest = text[: match.start()] + " " + text[match.end():]
    return found, rest


def parse_contact_line(line: str | None) -> ContactParts:
    rest = line or ""
    email, rest = _remove_first(EMAIL_RE, rest)
    phone, rest = _remove_first(PHONE_RE, rest)
    website, rest = _remove_first(URL_RE, rest)
    location = " ".join(re.split(r"[|·•,;]", rest)).strip()
    location = re.sub(r"\s{2,}", " ", location)
    return ContactParts(email, phone, website, location or None)


def split_skills(text: str | None) -> list[str]:
    if not text or not text.strip():
        return []
    parts: list[str] = []
    for chunk in re.split(r"[\n|•·▪,;]+", text):
        chunk = chunk.strip()
        if chunk:
            parts.append(chunk)
    return parts


def slugify(title: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", title.strip().lower()).strip("-")
    return f"custom-{slug}" if slug else "custom-section"
