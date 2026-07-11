"""FastAPI application factory and HTTP entrypoints.

Exposes the S0 walking-skeleton HTTP surface:
  * ``GET /``              service identity.
  * ``GET /health``        liveness probe (always 200 while the process is up).
  * ``GET /healthz/ready`` readiness probe (checks downstream dependencies).

Run locally with::

    uvicorn app.main:app --reload
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

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
    try:
        yield
    finally:
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

        For S0 this always reports ready. Later it will verify the RabbitMQ
        connection (and any other hard dependency) before returning 200.
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
