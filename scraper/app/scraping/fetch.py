"""SSRF-safe HTTP fetcher.

Fetches a user-supplied URL with the hardening mandated by ADR-0008:

* redirects are followed **manually** (``follow_redirects=False``) and every hop
  is re-validated with the SSRF guard before the request is dispatched;
* only an allowlisted set of content types is accepted;
* the body is streamed and capped at ``settings.max_content_bytes`` decoded
  bytes, aborting on overflow (decompression-bomb defense, R-SEC-05);
* the request is bounded by a timeout.
"""

from __future__ import annotations

import httpx

from ..config import Settings, get_settings
from ..logging_config import get_logger
from .ssrf import assert_url_allowed, validate_redirect

__all__ = ["FetchError", "ContentTooLargeError", "fetch_html"]

_log = get_logger(__name__)

# Content types that carry text we can extract. Anything else is refused so the
# scraper cannot be pointed at binaries or unexpected payloads (ADR-0008 §4).
_ALLOWED_CONTENT_TYPES = frozenset(
    {"text/html", "application/xhtml+xml", "text/plain"}
)

# A conservative browser-like User-Agent; some sites reject the default.
_DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (compatible; MarkitScraper/0.1; +https://markit.local)"
    ),
    "Accept": "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.1",
}


class FetchError(Exception):
    """Raised for any non-SSRF fetch failure (status, content type, network)."""


class ContentTooLargeError(Exception):
    """Raised when the response body exceeds the configured byte cap."""


def _content_type(response: httpx.Response) -> str:
    """Return the lower-cased media type (without parameters) of a response."""
    raw = response.headers.get("content-type", "")
    return raw.split(";", 1)[0].strip().lower()


async def _read_capped_body(response: httpx.Response, max_bytes: int) -> bytes:
    """Stream the response body, aborting if it exceeds ``max_bytes``.

    ``aiter_bytes`` yields already-decompressed bytes, so capping here bounds the
    *decoded* size and defends against decompression bombs (R-SEC-05).

    Args:
        response: An open streaming response.
        max_bytes: The maximum number of decoded bytes to accept.

    Returns:
        The full response body.

    Raises:
        ContentTooLargeError: If the decoded body exceeds ``max_bytes``.
    """
    chunks: list[bytes] = []
    total = 0
    async for chunk in response.aiter_bytes():
        total += len(chunk)
        if total > max_bytes:
            raise ContentTooLargeError(
                f"response body exceeded {max_bytes} bytes"
            )
        chunks.append(chunk)
    return b"".join(chunks)


async def fetch_html(url: str, *, settings: Settings | None = None) -> tuple[str, str]:
    """Fetch ``url`` safely and return its decoded body and final URL.

    Args:
        url: The absolute URL to fetch.
        settings: Optional settings override (defaults to the process settings).

    Returns:
        A ``(body_text, final_url)`` tuple where ``final_url`` is the URL after
        following any redirects.

    Raises:
        SsrfError: If ``url`` or any redirect hop is refused by the SSRF guard.
        ContentTooLargeError: If the body exceeds ``settings.max_content_bytes``.
        FetchError: On a bad status, disallowed content type, redirect without a
            ``Location``, too many redirects, or a transport error.
    """
    settings = settings or get_settings()
    current = url

    async with httpx.AsyncClient(
        follow_redirects=False,
        timeout=settings.request_timeout_seconds,
        headers=_DEFAULT_HEADERS,
    ) as client:
        for _ in range(settings.max_redirects + 1):
            # Re-validate every hop (initial URL included) before requesting it.
            assert_url_allowed(current)
            try:
                async with client.stream("GET", current) as response:
                    if response.is_redirect:
                        location = response.headers.get("location")
                        if not location:
                            raise FetchError(
                                f"redirect without Location from {current}"
                            )
                        # Re-validate the target before the next hop (raises
                        # SsrfError on a private/metadata redirect).
                        current = validate_redirect(current, location)
                        continue

                    if response.status_code >= 400:
                        raise FetchError(
                            f"HTTP {response.status_code} for {current}"
                        )

                    media_type = _content_type(response)
                    if media_type not in _ALLOWED_CONTENT_TYPES:
                        raise FetchError(
                            f"disallowed content type {media_type!r} for {current}"
                        )

                    body = await _read_capped_body(
                        response, settings.max_content_bytes
                    )
                    encoding = response.encoding or "utf-8"
                    text = body.decode(encoding, errors="replace")
                    return text, current
            except httpx.HTTPError as exc:
                raise FetchError(f"transport error for {current}: {exc}") from exc

    raise FetchError(f"too many redirects (> {settings.max_redirects}) for {url}")
