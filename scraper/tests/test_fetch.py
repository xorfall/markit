"""Tests for the SSRF-safe fetcher with network mocked via respx."""

from __future__ import annotations

import asyncio

import httpx
import pytest
import respx

from app.config import Settings
from app.scraping import ssrf
from app.scraping.fetch import ContentTooLargeError, FetchError, fetch_html
from app.scraping.ssrf import SsrfError


@pytest.fixture(autouse=True)
def _public_dns(monkeypatch) -> None:
    """Resolve every host to a public IP so no real DNS is performed."""
    monkeypatch.setattr(ssrf, "_resolve_host", lambda host: ["93.184.216.34"])


def _run(coro):
    return asyncio.run(coro)


def test_redirect_to_private_host_is_blocked(monkeypatch) -> None:
    # Arrange: the redirect target resolves to a private IP.
    def resolver(host: str) -> list[str]:
        return ["10.0.0.9"] if "internal" in host else ["93.184.216.34"]

    monkeypatch.setattr(ssrf, "_resolve_host", resolver)

    with respx.mock:
        respx.get("http://example.com/").mock(
            return_value=httpx.Response(
                302, headers={"location": "http://internal.test/secret"}
            )
        )

        # Act / Assert
        with pytest.raises(SsrfError):
            _run(fetch_html("http://example.com/", settings=Settings()))


def test_oversized_body_raises_content_too_large() -> None:
    # Arrange: body far exceeds the tiny cap.
    with respx.mock:
        respx.get("http://example.com/").mock(
            return_value=httpx.Response(
                200,
                headers={"content-type": "text/html"},
                content=b"a" * 5000,
            )
        )
        settings = Settings(max_content_bytes=100)

        # Act / Assert
        with pytest.raises(ContentTooLargeError):
            _run(fetch_html("http://example.com/", settings=settings))


def test_disallowed_content_type_raises_fetch_error() -> None:
    # Arrange
    with respx.mock:
        respx.get("http://example.com/").mock(
            return_value=httpx.Response(
                200,
                headers={"content-type": "application/pdf"},
                content=b"%PDF-1.4",
            )
        )

        # Act / Assert
        with pytest.raises(FetchError):
            _run(fetch_html("http://example.com/", settings=Settings()))


def test_happy_path_returns_html_and_final_url() -> None:
    # Arrange
    with respx.mock:
        respx.get("http://example.com/").mock(
            return_value=httpx.Response(
                200,
                headers={"content-type": "text/html; charset=utf-8"},
                content=b"<html><title>Hi</title></html>",
            )
        )

        # Act
        html, final_url = _run(fetch_html("http://example.com/", settings=Settings()))

        # Assert
        assert "<title>Hi</title>" in html
        assert final_url == "http://example.com/"
