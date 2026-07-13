"""SSRF-safe HTTP fetcher.

Fetches a user-supplied URL with the hardening mandated by ADR-0008:

* redirects are followed **manually** (``follow_redirects=False``) and every hop
  is re-validated with the SSRF guard before the request is dispatched;
* the connection is **pinned to the validated IP** (:class:`_PinnedTransport`),
  so the address contacted is the exact one the guard checked — no re-resolution
  window for DNS rebinding to exploit (ADR-0008 §5);
* only an allowlisted set of content types is accepted;
* the body is streamed and capped at ``settings.max_content_bytes`` decoded
  bytes, aborting on overflow (decompression-bomb defense, R-SEC-05);
* the request is bounded by a timeout.
"""

from __future__ import annotations

import httpx

from ..config import Settings, get_settings
from ..logging_config import get_logger
from .ssrf import resolve_and_validate, validate_redirect

__all__ = ["FetchError", "ContentTooLargeError", "fetch_html"]

_log = get_logger(__name__)


class _PinnedTransport(httpx.AsyncHTTPTransport):
    """Transport that connects to the exact IP the SSRF guard validated.

    The guard resolves a hostname and checks the resulting IP, but a normal HTTP
    client re-resolves the name when it opens the socket. A DNS-rebinding
    attacker exploits that gap: return a public IP for the guard's lookup, then a
    private one (``169.254.169.254``, ``127.0.0.1``, …) for the connect — a
    classic time-of-check-to-time-of-use (TOCTOU) bypass.

    This transport removes the gap. For a request carrying a ``pinned_ip``
    extension it rewrites the URL host to that IP (so the socket goes to the
    address that was actually checked) while preserving the original hostname for
    the ``Host`` header — already set by the client — and for the TLS
    ``sni_hostname`` extension, which httpcore uses as the ``server_hostname`` for
    SNI *and* certificate-hostname verification. The certificate is therefore
    still validated against the real name, not the IP.
    """

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        pinned_ip = request.extensions.get("pinned_ip")
        if pinned_ip:
            hostname = request.url.host
            request.url = request.url.copy_with(host=pinned_ip)
            # Keep TLS keyed to the real hostname: SNI + cert check use this, not
            # the pinned IP. (The Host header, set by the client, is untouched.)
            request.extensions = {**request.extensions, "sni_hostname": hostname}
        return await super().handle_async_request(request)

# Content types that carry text we can extract. Anything else is refused so the
# scraper cannot be pointed at binaries or unexpected payloads (ADR-0008 §4).
_ALLOWED_CONTENT_TYPES = frozenset(
    {"text/html", "application/xhtml+xml", "text/plain"}
)

# A realistic desktop-browser User-Agent — many sites reject library/bot agents
# with a 403. Sites behind a JS challenge (Cloudflare, Medium) still need the
# headless fallback, which the worker triggers when the static fetch is blocked.
_DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
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
        transport=_PinnedTransport(),
    ) as client:
        for _ in range(settings.max_redirects + 1):
            # Re-validate every hop (initial URL included) and pin to the checked
            # IP so the connection cannot be rebound to a private address.
            pinned_ip = resolve_and_validate(current)
            try:
                async with client.stream(
                    "GET", current, extensions={"pinned_ip": pinned_ip}
                ) as response:
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
