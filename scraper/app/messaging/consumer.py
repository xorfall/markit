"""RabbitMQ consumer that drives the scrape pipeline.

Consumes ``scrape.requested`` messages from the durable ``markit.scrape-requests``
queue, runs :func:`app.scraping.worker.handle_scrape_request`, and publishes the
results back to the topic exchange ``markit.events``. The exchange and queue are
declared idempotently with the same durable topology the Java backend declares,
so co-existing with an already-declared broker is safe.

The consumer reconnects on connection loss and exposes ``is_connected`` for the
readiness probe. A trace id from the incoming message headers is propagated into
the logs of the work it drives.
"""

from __future__ import annotations

import asyncio
import json
from typing import Any

import aio_pika
from aio_pika import ExchangeType, Message
from aio_pika.abc import (
    AbstractIncomingMessage,
    AbstractRobustConnection,
    AbstractRobustExchange,
)

from ..config import Settings
from ..logging_config import bind_trace_id, get_logger
from ..scraping.worker import ScrapeRequest, handle_scrape_request
from ..tracing import current_trace_id, extract_context, inject_headers, start_span

__all__ = [
    "ScrapeConsumer",
    "EXCHANGE_NAME",
    "REQUEST_QUEUE",
    "REQUEST_ROUTING_KEY",
]

_log = get_logger(__name__)

# Topology owned by the Java backend; declared here passively/idempotently.
EXCHANGE_NAME = "markit.events"
REQUEST_QUEUE = "markit.scrape-requests"
REQUEST_ROUTING_KEY = "scrape.requested"

# Header carrying a cross-service correlation id, if present.
_TRACE_HEADER = "x-trace-id"

# Seconds to wait before retrying after a connection failure.
_RECONNECT_DELAY_SECONDS = 5.0
# Bounded prefetch so a single worker is not flooded.
_PREFETCH_COUNT = 4


class ScrapeConsumer:
    """Owns the broker connection lifecycle and message dispatch."""

    def __init__(self, settings: Settings) -> None:
        """Initialise the consumer.

        Args:
            settings: Active application settings (broker coordinates, caps).
        """
        self._settings = settings
        self._connection: AbstractRobustConnection | None = None
        self._exchange: AbstractRobustExchange | None = None
        self._connected = False
        self._stop = asyncio.Event()

    @property
    def is_connected(self) -> bool:
        """Return whether the broker connection is currently established."""
        return self._connected

    def request_stop(self) -> None:
        """Signal the run loop to stop reconnecting and exit."""
        self._stop.set()

    async def run(self) -> None:
        """Connect, consume, and reconnect until :meth:`request_stop`.

        Runs as a background task; connection failures are logged and retried
        with a fixed backoff rather than propagated.
        """
        while not self._stop.is_set():
            try:
                await self._connect_and_consume()
            except asyncio.CancelledError:
                raise
            except Exception as exc:  # noqa: BLE001 - keep the loop alive
                self._connected = False
                _log.warning("consumer.connection_error", error=str(exc))
                await self._sleep_or_stop(_RECONNECT_DELAY_SECONDS)
        _log.info("consumer.stopped")

    async def _sleep_or_stop(self, delay: float) -> None:
        """Sleep for ``delay`` seconds unless a stop is requested first."""
        try:
            await asyncio.wait_for(self._stop.wait(), timeout=delay)
        except asyncio.TimeoutError:
            return

    async def _connect_and_consume(self) -> None:
        """Establish the connection, declare topology, and consume messages."""
        connection = await aio_pika.connect_robust(
            host=self._settings.rabbitmq_host,
            port=self._settings.rabbitmq_port,
            login=self._settings.rabbitmq_user,
            password=self._settings.rabbitmq_password,
            virtualhost=self._settings.rabbitmq_vhost,
            timeout=self._settings.request_timeout_seconds,
        )
        self._connection = connection
        try:
            channel = await connection.channel()
            await channel.set_qos(prefetch_count=_PREFETCH_COUNT)

            exchange = await channel.declare_exchange(
                EXCHANGE_NAME, ExchangeType.TOPIC, durable=True
            )
            self._exchange = exchange

            queue = await channel.declare_queue(REQUEST_QUEUE, durable=True)
            await queue.bind(exchange, routing_key=REQUEST_ROUTING_KEY)

            self._connected = True
            _log.info(
                "consumer.ready",
                exchange=EXCHANGE_NAME,
                queue=REQUEST_QUEUE,
                routing_key=REQUEST_ROUTING_KEY,
            )

            await queue.consume(self._on_message)
            # Block until stop is requested or the connection drops.
            await self._stop.wait()
        finally:
            self._connected = False
            await connection.close()

    async def _on_message(self, message: AbstractIncomingMessage) -> None:
        """Handle one incoming message; always acks (no poison requeue).

        Extracts the incoming W3C ``traceparent`` from the message headers and runs
        the work inside a child ``scrape.process`` span, so the Python scrape is part
        of the Java-initiated trace (NFR-OBS-004). Result publishes made from within
        this span re-inject the context for the return hop.
        """
        async with message.process(requeue=False):
            parent = extract_context(message.headers)
            with start_span("scrape.process", parent):
                # Prefer the propagated trace id (correlates logs with the trace);
                # fall back to the legacy correlation header when tracing is off.
                trace_id = current_trace_id() or self._extract_trace_id(message)
                if trace_id:
                    bind_trace_id(trace_id)
                try:
                    request = self._parse(message.body)
                except (ValueError, KeyError, json.JSONDecodeError) as exc:
                    _log.error("consumer.bad_message", error=str(exc))
                    return
                await handle_scrape_request(
                    request, self._publish, settings=self._settings
                )

    @staticmethod
    def _extract_trace_id(message: AbstractIncomingMessage) -> str | None:
        """Pull a correlation id from headers or ``correlation_id``."""
        headers = message.headers or {}
        raw = headers.get(_TRACE_HEADER) or message.correlation_id
        return str(raw) if raw else None

    @staticmethod
    def _parse(body: bytes) -> ScrapeRequest:
        """Parse a ``scrape.requested`` JSON body into a :class:`ScrapeRequest`."""
        payload: dict[str, Any] = json.loads(body)
        return ScrapeRequest(
            bookmark_id=payload["bookmarkId"],
            owner_id=payload.get("ownerId"),
            url=payload["url"],
        )

    async def _publish(self, routing_key: str, body: dict[str, Any]) -> None:
        """Publish a result to ``markit.events`` with the given routing key."""
        if self._exchange is None:
            raise RuntimeError("cannot publish before the exchange is declared")
        # Inject the active trace context so the Java ScrapeResultListener continues
        # the same trace (Python -> Java hop). Called from inside the `scrape.process`
        # span, so `traceparent` points at that span; a no-op when tracing is off.
        headers = inject_headers({})
        message = Message(
            body=json.dumps(body).encode("utf-8"),
            content_type="application/json",
            delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            headers=headers or None,
        )
        await self._exchange.publish(message, routing_key=routing_key)
