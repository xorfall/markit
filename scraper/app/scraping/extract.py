"""Two-phase content extraction.

Phase 1 (fast metadata) parses ``<title>`` and description/Open-Graph meta tags
from already-fetched HTML using the standard-library ``html.parser`` (no browser,
no heavy dependency). Phase 2 (full content) extracts the main article text with
``trafilatura``; if the static result is empty or below a small threshold, the
caller falls back to :func:`render_with_headless` (Playwright headless Chromium)
and re-extracts.

``trafilatura`` and ``playwright`` are imported **lazily** inside the functions
that use them so that the module imports (and tests run) without them installed.
"""

from __future__ import annotations

from html.parser import HTMLParser

from ..config import get_settings
from ..logging_config import get_logger
from .ssrf import SsrfError, assert_url_allowed

__all__ = [
    "extract_metadata",
    "extract_content",
    "render_with_headless",
]

_log = get_logger(__name__)


class _MetaParser(HTMLParser):
    """Collects ``<title>`` text and ``<meta>`` tags from an HTML document."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.title: str | None = None
        self.meta: dict[str, str] = {}
        self._in_title = False

    def handle_starttag(
        self, tag: str, attrs: list[tuple[str, str | None]]
    ) -> None:
        if tag == "title":
            self._in_title = True
            return
        if tag == "meta":
            attributes = {k.lower(): (v or "") for k, v in attrs}
            key = (attributes.get("name") or attributes.get("property") or "").lower()
            content = attributes.get("content")
            if key and content and key not in self.meta:
                self.meta[key] = content

    def handle_endtag(self, tag: str) -> None:
        if tag == "title":
            self._in_title = False

    def handle_data(self, data: str) -> None:
        if self._in_title:
            self.title = (self.title or "") + data


def extract_metadata(html: str, url: str) -> tuple[str, str]:
    """Extract a title and description from HTML (phase 1).

    Falls back to ``url`` as the title when no ``<title>``/``og:title`` is found
    (FR-SCR-001). Returns an empty description string when none is present.

    Args:
        html: The fetched HTML document.
        url: The URL the document came from (used as a title fallback).

    Returns:
        A ``(title, description)`` tuple; both are always strings.
    """
    parser = _MetaParser()
    try:
        parser.feed(html)
    except Exception as exc:  # noqa: BLE001 - malformed HTML must not crash phase 1
        _log.warning("extract.metadata_parse_failed", url=url, error=str(exc))

    title = (parser.title or "").strip()
    if not title:
        title = parser.meta.get("og:title", "").strip()
    if not title:
        title = url

    description = (
        parser.meta.get("description")
        or parser.meta.get("og:description")
        or ""
    ).strip()

    return title, description


def _trafilatura_extract(html: str, url: str) -> str:
    """Run ``trafilatura`` extraction (lazy import; monkeypatched in tests)."""
    import trafilatura  # imported lazily so the module loads without it

    result = trafilatura.extract(html, url=url)
    return result or ""


def extract_content(html: str, url: str, *, min_length: int | None = None) -> str:
    """Extract the main text content (phase 2).

    Returns ``""`` when the extracted content is empty or shorter than
    ``min_length``, signalling that the headless fallback should run (ADR-0009).

    Args:
        html: The HTML document to extract from.
        url: The source URL (improves ``trafilatura`` accuracy).
        min_length: Minimum acceptable content length; defaults to
            ``settings.min_content_length``. Pass ``0`` to accept any non-empty
            result (used after a headless render).

    Returns:
        The extracted plain-text content, or ``""`` to trigger the fallback.
    """
    if min_length is None:
        min_length = get_settings().min_content_length

    content = _trafilatura_extract(html, url).strip()
    if len(content) < min_length:
        return ""
    return content


async def render_with_headless(url: str) -> str:
    """Render ``url`` with headless Chromium and return the rendered HTML.

    Every sub-request the page issues is intercepted and re-validated with the
    SSRF guard (ADR-0009 / R-SEC-02): non-http(s) schemes and blocked IP targets
    are aborted so a page cannot pivot the browser to internal hosts.

    ``playwright`` is imported lazily; tests mock this function and never launch
    a real browser.

    Args:
        url: The URL to render (already validated by the caller).

    Returns:
        The rendered page HTML.
    """
    from playwright.async_api import Request, Route, async_playwright

    async def _guard(route: "Route", request: "Request") -> None:
        target = request.url
        try:
            # Reuse the exact same deny-list used for the top-level fetch.
            assert_url_allowed(target)
        except SsrfError:
            _log.warning("headless.subrequest_blocked", url=target)
            await route.abort()
            return
        await route.continue_()

    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        try:
            context = await browser.new_context()
            await context.route("**/*", _guard)
            page = await context.new_page()
            await page.goto(url, wait_until="networkidle")
            return await page.content()
        finally:
            await browser.close()
