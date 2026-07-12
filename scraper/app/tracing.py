"""OpenTelemetry tracing setup and cross-service propagation helpers.

Closes the RabbitMQ->Python hop of the end-to-end trace (NFR-OBS-004). When an
``OTLP_ENDPOINT`` is configured, :func:`setup_tracing` installs a real
``TracerProvider`` (service ``markit-scraper``) that batch-exports spans to Jaeger
over OTLP/HTTP and registers the W3C ``traceparent`` propagator. When it is not
configured — as in unit tests and local runs without a collector — everything
stays a no-op: no provider is installed, so ``get_tracer`` hands back OpenTelemetry's
non-recording tracer and the helpers below simply do nothing.

The propagation helpers let the AMQP consumer:
  * **extract** the incoming ``traceparent`` from ``scrape.requested`` headers and
    open a child span around the worker call (Java -> Python hop), and
  * **inject** the active context into outgoing result headers so the Java
    ``ScrapeResultListener`` continues the same trace (Python -> Java hop).
"""

from __future__ import annotations

from contextlib import contextmanager
from typing import Any, Iterator, Mapping

from .config import Settings
from .logging_config import get_logger

_log = get_logger(__name__)

_SERVICE_NAME = "markit-scraper"

# OpenTelemetry is a declared dependency, but we still degrade to a no-op if the
# packages are somehow absent so the service never fails to import/boot.
try:
    from opentelemetry import trace
    from opentelemetry.context import Context
    from opentelemetry.propagate import extract as _otel_extract
    from opentelemetry.propagate import inject as _otel_inject

    _OTEL_AVAILABLE = True
except ImportError:  # pragma: no cover - exercised only without OTel installed
    trace = None  # type: ignore[assignment]
    Context = Any  # type: ignore[assignment,misc]
    _OTEL_AVAILABLE = False


def setup_tracing(settings: Settings) -> None:
    """Initialise distributed tracing when opted in.

    No-op unless ``settings.otlp_endpoint`` is set (or the OpenTelemetry packages
    are unavailable), so the process never blocks on an unreachable collector and
    tests run without one.

    Args:
        settings: Active application settings.
    """
    if not settings.otlp_endpoint:
        _log.debug("tracing.disabled", reason="no OTLP_ENDPOINT configured")
        return

    if not _OTEL_AVAILABLE:  # pragma: no cover - defensive
        _log.warning("tracing.unavailable", reason="opentelemetry packages not installed")
        return

    from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
    from opentelemetry.propagate import set_global_textmap
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor
    from opentelemetry.trace.propagation.tracecontext import (
        TraceContextTextMapPropagator,
    )

    resource = Resource.create({"service.name": _SERVICE_NAME})
    provider = TracerProvider(resource=resource)
    # OTLP/HTTP: the endpoint is used as-is (the backend's `.../v1/traces` full URL is passed
    # straight through), matching the Java exporter so both land in the same Jaeger.
    exporter = OTLPSpanExporter(endpoint=settings.otlp_endpoint)
    provider.add_span_processor(BatchSpanProcessor(exporter))
    trace.set_tracer_provider(provider)
    # W3C `traceparent` so the context round-trips with the Spring AMQP producer/consumer.
    set_global_textmap(TraceContextTextMapPropagator())

    _log.info("tracing.enabled", endpoint=settings.otlp_endpoint, service=_SERVICE_NAME)


def extract_context(headers: Mapping[str, Any] | None) -> "Context | None":
    """Extract a trace context from incoming AMQP message headers.

    Args:
        headers: The delivered message headers (may carry ``traceparent``).

    Returns:
        The extracted OpenTelemetry context, or ``None`` when tracing is
        unavailable. A missing header simply yields an empty (root) context.
    """
    if not _OTEL_AVAILABLE:
        return None
    carrier = {k: v for k, v in (headers or {}).items() if isinstance(v, str)}
    return _otel_extract(carrier)


@contextmanager
def start_span(name: str, context: "Context | None" = None) -> Iterator[Any]:
    """Start ``name`` as the current span, optionally as a child of ``context``.

    A no-op that yields ``None`` when OpenTelemetry is unavailable; a
    non-recording span when tracing is not configured; a real span otherwise.

    Args:
        name: Span name (e.g. ``scrape.process``).
        context: Parent context extracted from the incoming message, if any.
    """
    if not _OTEL_AVAILABLE:
        yield None
        return
    tracer = trace.get_tracer(_SERVICE_NAME)
    with tracer.start_as_current_span(name, context=context) as span:
        yield span


def inject_headers(headers: dict[str, Any]) -> dict[str, Any]:
    """Inject the active trace context into outgoing AMQP headers.

    Adds a ``traceparent`` entry when a span is active so the Java result
    listener continues the same trace. No-op otherwise.

    Args:
        headers: The header dict to enrich in place.

    Returns:
        The same ``headers`` dict, for convenient chaining.
    """
    if _OTEL_AVAILABLE:
        _otel_inject(headers)
    return headers


def current_trace_id() -> str | None:
    """Return the active span's trace id as 32-char hex, or ``None``.

    Used to populate the structured-log ``trace_id`` so scraper logs correlate
    with the propagated trace. Returns ``None`` when no valid span is active.
    """
    if not _OTEL_AVAILABLE:
        return None
    span_context = trace.get_current_span().get_span_context()
    if not span_context.is_valid:
        return None
    return format(span_context.trace_id, "032x")
