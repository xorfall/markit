"""FastAPI application factory and HTTP entrypoints.

Exposes the S0 walking-skeleton HTTP surface:
  * ``GET /``              service identity.
  * ``GET /health``        liveness probe (always 200 while the process is up).
  * ``GET /healthz/ready`` readiness probe (checks downstream dependencies).

Run locally with::

    uvicorn app.main:app --reload
"""

from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager, suppress
from typing import AsyncIterator

from fastapi import FastAPI
from prometheus_client import make_asgi_app

from . import __version__
from .config import Settings, get_settings
from .logging_config import configure_logging, get_logger
from .tracing import setup_tracing

_SERVICE_NAME = "markit-scraper"


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Configure logging and optional tracing on startup.

    Args:
        app: The FastAPI application being started.
    """
    settings = get_settings()
    configure_logging(settings.log_level)
    setup_tracing(settings)

    log = get_logger(__name__)
    log.info("service.startup", service=_SERVICE_NAME, version=__version__)

    # Start the RabbitMQ consumer as a background task. Imported lazily so the
    # HTTP surface (and its tests) never requires the AMQP dependency at import
    # time. The task reconnects on its own; startup never blocks on the broker.
    from .messaging.consumer import ScrapeConsumer

    consumer = ScrapeConsumer(settings)
    app.state.consumer = consumer
    consumer_task = asyncio.create_task(consumer.run(), name="scrape-consumer")
    try:
        yield
    finally:
        consumer.request_stop()
        consumer_task.cancel()
        with suppress(asyncio.CancelledError):
            await consumer_task
        log.info("service.shutdown", service=_SERVICE_NAME)


def create_app() -> FastAPI:
    """Build and configure the FastAPI application.

    Returns:
        A fully wired ``FastAPI`` instance.
    """
    app = FastAPI(
        title="Markit Scraper",
        version=__version__,
        lifespan=lifespan,
    )
    _register_routes(app)
    # Import the worker so its module-level counters register in the default registry and
    # therefore appear on /metrics from process start (before the first scrape runs).
    from .scraping import worker  # noqa: F401

    # Prometheus exposition (NFR-OBS-*): scrape-worker RED + domain metrics live in
    # app.scraping.worker; the ASGI app renders them from the default registry.
    app.mount("/metrics", make_asgi_app())
    return app


def _register_routes(app: FastAPI) -> None:
    """Attach the S0 HTTP routes to ``app``."""

    @app.get("/")
    async def root() -> dict[str, str]:
        """Return service identity."""
        return {"service": _SERVICE_NAME, "version": __version__}

    @app.get("/health")
    async def health() -> dict[str, str]:
        """Liveness probe: 200 as long as the process is running."""
        return {"status": "ok"}

    @app.get("/healthz/ready")
    async def readiness(settings: Settings = _settings_dep()) -> dict[str, str]:
        """Readiness probe.

        Reports ``ready`` for the HTTP surface. Broker connectivity is observable
        via ``app.state.consumer.is_connected`` and the consumer logs; it is
        intentionally not a hard gate here so the process stays serviceable while
        the consumer reconnects in the background.
        """
        return {"status": "ready"}


def _settings_dep() -> Settings:
    """Provide settings as a FastAPI dependency default.

    Wrapped in a helper so the dependency is resolved lazily at request time
    rather than at import time.
    """
    from fastapi import Depends

    return Depends(get_settings)


app = create_app()
