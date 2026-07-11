"""Tests for two-phase extraction (trafilatura/playwright mocked)."""

from __future__ import annotations

from app.scraping import extract
from app.scraping.extract import extract_content, extract_metadata


def test_extract_metadata_parses_title_and_description() -> None:
    # Arrange
    html = (
        "<html><head>"
        "<title>Hello World</title>"
        '<meta name="description" content="A short summary.">'
        "</head><body>x</body></html>"
    )

    # Act
    title, description = extract_metadata(html, "http://example.com/page")

    # Assert
    assert title == "Hello World"
    assert description == "A short summary."


def test_extract_metadata_falls_back_to_open_graph() -> None:
    # Arrange
    html = (
        "<html><head>"
        '<meta property="og:title" content="OG Title">'
        '<meta property="og:description" content="OG Desc">'
        "</head></html>"
    )

    # Act
    title, description = extract_metadata(html, "http://example.com/page")

    # Assert
    assert title == "OG Title"
    assert description == "OG Desc"


def test_extract_metadata_falls_back_to_url_as_title() -> None:
    # Arrange
    html = "<html><head></head><body>no title here</body></html>"

    # Act
    title, description = extract_metadata(html, "http://example.com/page")

    # Assert
    assert title == "http://example.com/page"
    assert description == ""


def test_extract_content_returns_empty_when_trafilatura_empty(monkeypatch) -> None:
    # Arrange: mocked trafilatura yields nothing -> signals headless fallback.
    monkeypatch.setattr(extract, "_trafilatura_extract", lambda html, url: "")

    # Act
    content = extract_content("<html>x</html>", "http://example.com/")

    # Assert
    assert content == ""


def test_extract_content_returns_text_above_threshold(monkeypatch) -> None:
    # Arrange
    monkeypatch.setattr(
        extract, "_trafilatura_extract", lambda html, url: "A" * 500
    )

    # Act
    content = extract_content(
        "<html>x</html>", "http://example.com/", min_length=10
    )

    # Assert
    assert content == "A" * 500


def test_extract_content_below_threshold_triggers_fallback(monkeypatch) -> None:
    # Arrange: non-empty but too short -> empty signal for headless fallback.
    monkeypatch.setattr(extract, "_trafilatura_extract", lambda html, url: "tiny")

    # Act
    content = extract_content(
        "<html>x</html>", "http://example.com/", min_length=100
    )

    # Assert
    assert content == ""
