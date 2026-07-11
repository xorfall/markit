"""OpenTelemetry tracing setup (placeholder / opt-in).

S0 does NOT require an OTLP collector to boot. This module exposes a single
``setup_tracing`` hook that is a no-op unless an ``OTLP_ENDPOINT`` is configured
*and* the OpenTelemetry packages are installed. The concrete wiring is left
commented out so the service has zero hard dependency on a collector.

Later phases will:
  * create a ``TracerProvider`` with a service-name ``Resource``,
  * install an OTLP span exporter pointed at ``settings.otlp_endpoint``,
  * auto-instrument FastAPI, the AMQP client, and outbound HTTP fetches.
"""

from __future__ import annotations

from .config import Settings
from .logging_config import get_logger

_log = get_logger(__name__)


def setup_tracing(settings: Settings) -> None:
    """Initialise distributed tracing when opted in.

    No-op unless ``settings.otlp_endpoint`` is set. Even when set, S0 only logs
    intent rather than establishing an exporter, so the process never blocks on
    an unavailable collector.

    Args:
        settings: Active application settings.
    """
    if not settings.otlp_endpoint:
        _log.debug("tracing.disabled", reason="no OTLP_ENDPOINT configured")
        return

    # --- Opt-in wiring (enable in a later phase) -------------------------
    # from opentelemetry import trace
    # from opentelemetry.sdk.resources import Resource
    # from opentelemetry.sdk.trace import TracerProvider
    # from opentelemetry.sdk.trace.export import BatchSpanProcessor
    # from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import (
    #     OTLPSpanExporter,
    # )
    #
    # resource = Resource.create({"service.name": "markit-scraper"})
    # provider = TracerProvider(resource=resource)
    # exporter = OTLPSpanExporter(endpoint=settings.otlp_endpoint)
    # provider.add_span_processor(BatchSpanProcessor(exporter))
    # trace.set_tracer_provider(provider)
    # ---------------------------------------------------------------------

    _log.info("tracing.opt_in_pending", endpoint=settings.otlp_endpoint)
