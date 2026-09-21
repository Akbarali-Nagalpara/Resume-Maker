"""Contact mapping, date ranges and stack tokens (no LLM, deterministic)."""

from app.utils import classifier as clf


def test_contact_fields_split():
    parts = clf.parse_contact_line(
        "+91 72010 91900 • nagalparaakbarali03@gmail.com • "
        "linkedin.com/in/akbarali-nagalpara • github.com/Akbarali-Nagalpara Bengaluru India"
    )
    assert parts.email == "nagalparaakbarali03@gmail.com"
    assert parts.phone == "+91 72010 91900"
    assert parts.github == "github.com/Akbarali-Nagalpara"
    assert parts.linkedin == "linkedin.com/in/akbarali-nagalpara"
    assert parts.location == "Bengaluru, India"
    assert parts.website is None


def test_bare_tech_words_are_not_urls():
    assert clf.URL_RE.search("mayachen.dev")
    assert clf.URL_RE.search("https://example.com/x")
    assert not clf.URL_RE.search("B.Tech Computer Science")
    assert not clf.URL_RE.search("React.js")


def test_month_year_ranges():
    assert clf.contains_date_range("Feb 2026 – Aug 2026")
    assert clf.contains_date_range("2024–2026")
    assert not clf.contains_date_range("+91 72010 91900")
    assert not clf.contains_date_range("B.Tech Computer Science")
