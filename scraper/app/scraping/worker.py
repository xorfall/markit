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

from dataclasses import dataclass
from typing import Any, Awaitable, Callable

from ..config import Settings, get_settings
from ..logging_config import get_logger
from .extract import extract_content, extract_metadata, render_with_headless
from .fetch import ContentTooLargeError, fetch_html
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

        html, final_url = await fetch_html(request.url, settings=settings)

        title, description = extract_metadata(html, final_url)
        await publish(
            ROUTING_METADATA_READY,
            {
                "bookmarkId": bookmark_id,
                "title": title,
                "description": description,
            },
        )

        content = extract_content(
            html, final_url, min_length=settings.min_content_length
        )
        if not content:
            _log.info("scrape.headless_fallback", bookmark_id=bookmark_id, url=final_url)
            rendered = await render_with_headless(request.url)
            # After a headless render accept any non-empty extraction.
            content = extract_content(rendered, final_url, min_length=0)

        if len(content.encode("utf-8")) > settings.max_content_bytes:
            raise ContentTooLargeError(
                f"extracted content exceeded {settings.max_content_bytes} bytes"
            )

        await publish(
            ROUTING_CONTENT_COMPLETED,
            {"bookmarkId": bookmark_id, "content": content},
        )
        _log.info("scrape.completed", bookmark_id=bookmark_id, url=final_url)

    except SsrfError as exc:
        await _publish_failure(publish, bookmark_id, _REASON_UNSAFE_URL, exc)
    except ContentTooLargeError as exc:
        await _publish_failure(publish, bookmark_id, _REASON_CONTENT_TOO_LARGE, exc)
    except Exception as exc:  # noqa: BLE001 - handler must never propagate
        await _publish_failure(publish, bookmark_id, _REASON_FETCH_ERROR, exc)


async def _publish_failure(
    publish: Publisher, bookmark_id: str, reason: str, error: Exception
) -> None:
    """Emit a single ``scrape.failed`` result, guarding the publish itself."""
    _log.warning(
        "scrape.failed", bookmark_id=bookmark_id, reason=reason, error=str(error)
    )
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
