"""Tests for the scrape worker orchestration (fetch/extract/browser mocked)."""

from __future__ import annotations

import asyncio

import pytest

from app.scraping import worker as worker_mod
from app.scraping.fetch import ContentTooLargeError
from app.scraping.ssrf import SsrfError
from app.scraping.worker import ScrapeRequest, handle_scrape_request


class _FakePublisher:
    """Records ``(routing_key, body)`` publish calls in order."""

    def __init__(self) -> None:
        self.calls: list[tuple[str, dict]] = []

    async def __call__(self, routing_key: str, body: dict) -> None:
        self.calls.append((routing_key, body))

    @property
    def routing_keys(self) -> list[str]:
        return [rk for rk, _ in self.calls]


def _run(coro):
    return asyncio.run(coro)


@pytest.fixture()
def _allow_url(monkeypatch):
    """Neutralise the top-level SSRF check (network is mocked elsewhere)."""
    monkeypatch.setattr(worker_mod, "assert_url_allowed", lambda url: None)


def test_happy_path_publishes_metadata_then_content(monkeypatch, _allow_url) -> None:
    # Arrange
    async def fake_fetch(url, *, settings=None):
        return "<html>..</html>", "http://example.com/final"

    monkeypatch.setattr(worker_mod, "fetch_html", fake_fetch)
    monkeypatch.setattr(worker_mod, "extract_metadata", lambda h, u: ("Title", "Desc"))
    monkeypatch.setattr(
        worker_mod, "extract_content", lambda h, u, min_length=None: "CONTENT"
    )
    publisher = _FakePublisher()
    request = ScrapeRequest("bm-1", "owner-1", "http://example.com/")

    # Act
    _run(handle_scrape_request(request, publisher))

    # Assert
    assert publisher.routing_keys == [
        "scrape.metadata-ready",
        "scrape.content-completed",
    ]
    assert publisher.calls[0][1] == {
        "bookmarkId": "bm-1",
        "title": "Title",
        "description": "Desc",
    }
    assert publisher.calls[1][1] == {"bookmarkId": "bm-1", "content": "CONTENT"}


def test_headless_fallback_when_static_content_empty(monkeypatch, _allow_url) -> None:
    # Arrange: static extraction empty, headless render yields content.
    async def fake_fetch(url, *, settings=None):
        return "<html>static</html>", "http://example.com/"

    async def fake_render(url):
        return "<html>rendered</html>"

    contents = iter(["", "RENDERED"])
    monkeypatch.setattr(worker_mod, "fetch_html", fake_fetch)
    monkeypatch.setattr(worker_mod, "extract_metadata", lambda h, u: ("T", "D"))
    monkeypatch.setattr(
        worker_mod, "extract_content", lambda h, u, min_length=None: next(contents)
    )
    monkeypatch.setattr(worker_mod, "render_with_headless", fake_render)
    publisher = _FakePublisher()
    request = ScrapeRequest("bm-2", "owner-1", "http://example.com/")

    # Act
    _run(handle_scrape_request(request, publisher))

    # Assert
    assert publisher.routing_keys == [
        "scrape.metadata-ready",
        "scrape.content-completed",
    ]
    assert publisher.calls[1][1] == {"bookmarkId": "bm-2", "content": "RENDERED"}


def test_ssrf_blocked_url_publishes_single_unsafe_failure(monkeypatch) -> None:
    # Arrange
    def raise_ssrf(url):
        raise SsrfError("blocked")

    monkeypatch.setattr(worker_mod, "assert_url_allowed", raise_ssrf)
    publisher = _FakePublisher()
    request = ScrapeRequest("bm-3", "owner-1", "http://169.254.169.254/")

    # Act
    _run(handle_scrape_request(request, publisher))

    # Assert
    assert publisher.calls == [
        ("scrape.failed", {"bookmarkId": "bm-3", "reason": "UNSAFE_URL"})
    ]


def test_oversized_content_publishes_content_too_large(monkeypatch, _allow_url) -> None:
    # Arrange
    async def fake_fetch(url, *, settings=None):
        raise ContentTooLargeError("too big")

    monkeypatch.setattr(worker_mod, "fetch_html", fake_fetch)
    publisher = _FakePublisher()
    request = ScrapeRequest("bm-4", "owner-1", "http://example.com/")

    # Act
    _run(handle_scrape_request(request, publisher))

    # Assert
    assert publisher.calls == [
        ("scrape.failed", {"bookmarkId": "bm-4", "reason": "CONTENT_TOO_LARGE"})
    ]


def test_unexpected_error_maps_to_fetch_error(monkeypatch, _allow_url) -> None:
    # Arrange
    async def fake_fetch(url, *, settings=None):
        raise RuntimeError("boom")

    monkeypatch.setattr(worker_mod, "fetch_html", fake_fetch)
    publisher = _FakePublisher()
    request = ScrapeRequest("bm-5", "owner-1", "http://example.com/")

    # Act
    _run(handle_scrape_request(request, publisher))

    # Assert
    assert publisher.calls == [
        ("scrape.failed", {"bookmarkId": "bm-5", "reason": "FETCH_ERROR"})
    ]
