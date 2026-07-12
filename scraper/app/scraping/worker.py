"""Scrape orchestration for a single request.

Wires the SSRF guard, fetcher and two-phase extractor together and publishes the
results back to the Java backend over RabbitMQ. The public entrypoint,
:func:`handle_scrape_request`, is designed to **never raise**: it always emits
exactly one terminal result (``scrape.content-completed`` or ``scrape.failed``)
plus at most one preliminary ``scrape.metadata-ready``.

Routing keys and JSON bodies are the contract with the Java ``ScrapeResultListener``:

* ``scrape.metadata-ready``    → ``{"bookmarkId", "title", "description"}``
* ``scrape.content-completed`` → ``{"bookmarkId", "content"}``
* ``scrape.failed``            → ``{"bookmarkId", "reason"}`` where reason is one
  of ``UNSAFE_URL`` | ``CONTENT_TOO_LARGE`` | ``FETCH_ERROR``.
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any, Awaitable, Callable

from prometheus_client import Counter, Gauge, Histogram

from ..config import Settings, get_settings
from ..logging_config import get_logger
from .extract import extract_content, extract_metadata, render_with_headless
from .fetch import ContentTooLargeError, FetchError, fetch_html
from .ssrf import SsrfError, assert_url_allowed

__all__ = [
    "ScrapeRequest",
    "handle_scrape_request",
    "ROUTING_METADATA_READY",
    "ROUTING_CONTENT_COMPLETED",
    "ROUTING_FAILED",
]

_log = get_logger(__name__)

ROUTING_METADATA_READY = "scrape.metadata-ready"
ROUTING_CONTENT_COMPLETED = "scrape.content-completed"
ROUTING_FAILED = "scrape.failed"

# scrape.failed reasons (mirror the Java enum contract).
_REASON_UNSAFE_URL = "UNSAFE_URL"
_REASON_CONTENT_TOO_LARGE = "CONTENT_TOO_LARGE"
_REASON_FETCH_ERROR = "FETCH_ERROR"

# Scrape tiers (ADR-0009): a fast static fetch, with a headless browser as fallback.
_TIER_STATIC = "static"
_TIER_HEADLESS = "headless"

# --- Prometheus metrics (C6 crown-jewel: scrape worker RED + domain) -------------------
# Module-level so importing this module is side-effect-safe and the counters are shared
# process-wide; scraped from the FastAPI ``/metrics`` endpoint.
SCRAPE_TOTAL = Counter(
    "markit_scrape_total",
    "Terminal scrape outcomes, by result and failure reason (R-SCR-01, R-SEC-02/05).",
    ["result", "reason"],
)
SCRAPE_DURATION = Histogram(
    "markit_scrape_duration_seconds",
    "Wall-clock duration of each scrape phase, by tier (NFR-PERF-003).",
    ["tier"],
)
SCRAPE_TIER_TOTAL = Counter(
    "markit_scrape_tier_total",
    "Number of scrapes that exercised each tier (static vs headless ratio, ADR-0009 cost).",
    ["tier"],
)
HEADLESS_SESSIONS_ACTIVE = Gauge(
    "markit_headless_sessions_active",
    "Playwright headless renders currently in flight (resource saturation).",
)

# ``publish(routing_key, body)`` — supplied by the messaging layer.
Publisher = Callable[[str, dict[str, Any]], Awaitable[None]]


@dataclass(frozen=True)
class ScrapeRequest:
    """A single scrape job consumed from ``markit.scrape-requests``.

    Attributes:
        bookmark_id: The bookmark the result must be correlated with.
        owner_id: The owning user (carried for logging/authz context).
        url: The user-supplied URL to scrape.
    """

    bookmark_id: str
    owner_id: str | None
    url: str


async def handle_scrape_request(
    request: ScrapeRequest,
    publish: Publisher,
    *,
    settings: Settings | None = None,
) -> None:
    """Run the full scrape pipeline for one request and publish the result.

    Never raises: every failure is mapped to a single ``scrape.failed`` message.

    Args:
        request: The scrape job to process.
        publish: Async callback publishing ``(routing_key, body)`` to the broker.
        settings: Optional settings override.
    """
    settings = settings or get_settings()
    bookmark_id = request.bookmark_id

    try:
        assert_url_allowed(request.url)

        final_url = request.url
        title = ""
        description = ""
        content = ""
        metadata_published = False

        # Phase 1 — static fetch (fast). If the site blocks the request (e.g. a 403
        # from a bot filter) we do NOT give up: we fall back to a headless render.
        SCRAPE_TIER_TOTAL.labels(tier=_TIER_STATIC).inc()
        _static_started = time.perf_counter()
        try:
            html, final_url = await fetch_html(request.url, settings=settings)
            title, description = extract_metadata(html, final_url)
            await _publish_metadata(publish, bookmark_id, title, description)
            metadata_published = True
            content = extract_content(
                html, final_url, min_length=settings.min_content_length
            )
        except FetchError as exc:
            _log.info(
                "scrape.static_blocked",
                bookmark_id=bookmark_id,
                error=str(exc),
            )
        finally:
            SCRAPE_DURATION.labels(tier=_TIER_STATIC).observe(
                time.perf_counter() - _static_started
            )

        # Phase 2 — headless render for JS-heavy or bot-blocked pages (ADR-0009).
        if not content:
            _log.info("scrape.headless_fallback", bookmark_id=bookmark_id, url=request.url)
            SCRAPE_TIER_TOTAL.labels(tier=_TIER_HEADLESS).inc()
            _headless_started = time.perf_counter()
            HEADLESS_SESSIONS_ACTIVE.inc()
            try:
                rendered = await render_with_headless(request.url)
            finally:
                HEADLESS_SESSIONS_ACTIVE.dec()
                SCRAPE_DURATION.labels(tier=_TIER_HEADLESS).observe(
                    time.perf_counter() - _headless_started
                )
            if not metadata_published:
                title, description = extract_metadata(rendered, request.url)
                await _publish_metadata(publish, bookmark_id, title, description)
                metadata_published = True
            content = extract_content(rendered, request.url, min_length=0)

        # Quality gate: a page that yields too little real text (a 404 shell, a
        # login/paywall wall, or pure navigation chrome) is an honest failure — never
        # a silently "indexed" bookmark whose content is junk (the "fake indexed" bug).
        if len(content.strip()) < settings.min_content_length:
            raise FetchError(
                "content too thin — likely blocked, paywalled, or not an article"
            )

        if len(content.encode("utf-8")) > settings.max_content_bytes:
            raise ContentTooLargeError(
                f"extracted content exceeded {settings.max_content_bytes} bytes"
            )

        await publish(
            ROUTING_CONTENT_COMPLETED,
            {"bookmarkId": bookmark_id, "content": content},
        )
        SCRAPE_TOTAL.labels(result="ok", reason="").inc()
        _log.info("scrape.completed", bookmark_id=bookmark_id, url=final_url)

    except SsrfError as exc:
        await _publish_failure(publish, bookmark_id, _REASON_UNSAFE_URL, exc)
    except ContentTooLargeError as exc:
        await _publish_failure(publish, bookmark_id, _REASON_CONTENT_TOO_LARGE, exc)
    except Exception as exc:  # noqa: BLE001 - handler must never propagate
        await _publish_failure(publish, bookmark_id, _REASON_FETCH_ERROR, exc)


async def _publish_metadata(
    publish: Publisher, bookmark_id: str, title: str, description: str
) -> None:
    """Emit the phase-1 ``scrape.metadata-ready`` result (fast title/description)."""
    await publish(
        ROUTING_METADATA_READY,
        {"bookmarkId": bookmark_id, "title": title, "description": description},
    )


async def _publish_failure(
    publish: Publisher, bookmark_id: str, reason: str, error: Exception
) -> None:
    """Emit a single ``scrape.failed`` result, guarding the publish itself."""
    _log.warning(
        "scrape.failed", bookmark_id=bookmark_id, reason=reason, error=str(error)
    )
    SCRAPE_TOTAL.labels(result="failed", reason=reason).inc()
    try:
        await publish(
            ROUTING_FAILED, {"bookmarkId": bookmark_id, "reason": reason}
        )
    except Exception as publish_error:  # noqa: BLE001 - last-resort safety
        _log.error(
            "scrape.publish_failed",
            bookmark_id=bookmark_id,
            error=str(publish_error),
        )
