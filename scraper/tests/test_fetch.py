"""Tests for the SSRF-safe fetcher with network mocked via respx.

The fetcher pins the connection to the IP the SSRF guard validated (ADR-0008 §5),
so the request that leaves the client targets that IP — the ``Host`` header keeps
the original name. respx matches on the wire URL, hence the routes below are
registered at the resolved IP, not the hostname.
"""

from __future__ import annotations

import asyncio

import httpx
import pytest
import respx

from app.config import Settings
from app.scraping import ssrf
from app.scraping.fetch import ContentTooLargeError, FetchError, fetch_html
from app.scraping.ssrf import SsrfError

# Every host in these tests resolves here (see ``_public_dns``); the pinned
# connection therefore targets this address on the wire.
_PUBLIC_IP = "93.184.216.34"


@pytest.fixture(autouse=True)
def _public_dns(monkeypatch) -> None:
    """Resolve every host to a public IP so no real DNS is performed."""
    monkeypatch.setattr(ssrf, "_resolve_host", lambda host: [_PUBLIC_IP])


def _run(coro):
    return asyncio.run(coro)


def test_redirect_to_private_host_is_blocked(monkeypatch) -> None:
    # Arrange: the redirect target resolves to a private IP.
    def resolver(host: str) -> list[str]:
        return ["10.0.0.9"] if "internal" in host else [_PUBLIC_IP]

    monkeypatch.setattr(ssrf, "_resolve_host", resolver)

    with respx.mock:
        # example.com is pinned to the public IP; it 302s toward a private host.
        respx.get(f"http://{_PUBLIC_IP}/").mock(
            return_value=httpx.Response(
                302, headers={"location": "http://internal.test/secret"}
            )
        )

        # Act / Assert: the redirect target is refused before any request to it.
        with pytest.raises(SsrfError):
            _run(fetch_html("http://example.com/", settings=Settings()))


def test_oversized_body_raises_content_too_large() -> None:
    # Arrange: body far exceeds the tiny cap.
    with respx.mock:
        respx.get(f"http://{_PUBLIC_IP}/").mock(
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
        respx.get(f"http://{_PUBLIC_IP}/").mock(
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
        respx.get(f"http://{_PUBLIC_IP}/").mock(
            return_value=httpx.Response(
                200,
                headers={"content-type": "text/html; charset=utf-8"},
                content=b"<html><title>Hi</title></html>",
            )
        )

        # Act
        html, final_url = _run(fetch_html("http://example.com/", settings=Settings()))

        # Assert: the returned URL keeps the human-facing hostname, not the pin.
        assert "<title>Hi</title>" in html
        assert final_url == "http://example.com/"


def test_connection_is_pinned_to_validated_ip() -> None:
    """The socket targets the checked IP while ``Host`` keeps the hostname.

    This is the anti-rebinding guarantee: because the client connects to the
    exact address the guard validated, a second (malicious) DNS answer can never
    redirect the connection to a private range.
    """
    with respx.mock:
        route = respx.get(f"http://{_PUBLIC_IP}/").mock(
            return_value=httpx.Response(
                200,
                headers={"content-type": "text/html"},
                content=b"<html>ok</html>",
            )
        )

        # Act
        _run(fetch_html("http://example.com/", settings=Settings()))

        # Assert: wire target is the pinned IP; Host header is the original name.
        assert route.called
        sent = route.calls.last.request
        assert sent.url.host == _PUBLIC_IP
        assert sent.headers["host"] == "example.com"
