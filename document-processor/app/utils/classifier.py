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
# Bare domains only count with a real TLD ("React.js", "B.Tech", "Node.js"
# must NOT match) and only in standalone/delimited position ("Visit
# foo.tech today" stays plain text; contact lines use delimiters).
_TLDS = r"com|org|net|io|dev|me|in|co|ai|app|tech|info|us|uk|edu|online|site"
URL_RE = re.compile(
    rf"(https?://[\w./-]+|[\w-]+\.(?:{_TLDS})(?:/[\w./-]*)?(?=$|\s*[|•/,;:]))",
    re.IGNORECASE,
)
_MONTH = r"(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*"
DATE_RANGE_RE = re.compile(
    rf"(?:\b{_MONTH}\s+)?(19|20)\d{{2}}\s*[—–\-to]+\s*(?:\b{_MONTH}\s+)?"
    r"((19|20)\d{2}|present|current)",
    re.IGNORECASE,
)
BULLET_RE = re.compile(r"^(?:[•▪‣▶▸→⇒]\s*|[-*o]\s+|\d+[.)]\s+)")


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


def is_section_heading(line: str | None, styled: bool = False, seen_known: bool = False) -> bool:
    """Authoritative heading test for block splitters.

    Bullet lines are never headings. Unstyled keyword lines must be short and
    title-shaped; ALL-CAPS titles additionally wait for the first real section
    so contact blocks stay in the preamble.
    """
    text = (line or "").strip()
    if not text or is_bullet(text):
        return False
    if styled:
        return True
    if len(text) > 60 or text.endswith((".", ",")):
        return False
    # Date/GPA lines belong to items, never start sections.
    if contains_date_range(text):
        return False
    keyword = classify_heading(text) is not None
    if keyword and len(text) <= 32:
        return True
    if not seen_known:
        return False
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
    github: str | None = None
    linkedin: str | None = None
    location: str | None = None


def _remove_first(pattern: re.Pattern, text: str) -> tuple[str | None, str]:
    match = pattern.search(text)
    if not match:
        return None, text
    found = match.group().strip()
    rest = text[: match.start()] + " " + text[match.end():]
    return found, rest


GITHUB_RE = re.compile(r"(?:https?://)?(?:www\.)?github\.com/[\w.-]+/?", re.IGNORECASE)
LINKEDIN_RE = re.compile(
    r"(?:https?://)?(?:www\.)?linkedin\.com/in/[\w.-]+/?", re.IGNORECASE
)


def parse_contact_line(line: str | None) -> ContactParts:
    rest = line or ""
    email, rest = _remove_first(EMAIL_RE, rest)
    phone, rest = _remove_first(PHONE_RE, rest)
    github, rest = _remove_first(GITHUB_RE, rest)
    linkedin, rest = _remove_first(LINKEDIN_RE, rest)
    website, rest = _remove_first(URL_RE, rest)
    location = normalize_location(rest)
    return ContactParts(email, phone, website, github, linkedin, location)


def normalize_location(rest: str | None) -> str | None:
    """Rebuilds a location from leftover fragments, dropping URL/email/phone
    parts that leaked in. Never returns contact data as a location."""
    if not rest:
        return None
    fragments: list[str] = []
    for chunk in re.split(r"[|·•,;]", rest):
        chunk = chunk.strip()
        if not chunk:
            continue
        if URL_RE.search(chunk) or EMAIL_RE.search(chunk) or PHONE_RE.search(chunk):
            continue
        if "@" in chunk:
            continue
        fragments.append(chunk)
    if not fragments:
        return None
    location = ", ".join(fragments)
    location = re.sub(r"\s{2,}", " ", location).strip(" ,")
    if "," in location:
        return location
    # "Bengaluru India" -> "Bengaluru, India" (two capitalized words).
    words = location.split()
    if len(words) == 2 and all(w[:1].isupper() and w[1:].islower() for w in words):
        location = f"{words[0]}, {words[1]}"
    return location or None


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
