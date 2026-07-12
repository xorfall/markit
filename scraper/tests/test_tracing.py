"""Tests for the OpenTelemetry tracing wiring (no collector required)."""

from __future__ import annotations

from app.config import Settings
from app.tracing import (
    current_trace_id,
    extract_context,
    inject_headers,
    setup_tracing,
)


def test_setup_tracing_is_noop_when_endpoint_empty() -> None:
    # Arrange: no OTLP endpoint configured.
    settings = Settings(otlp_endpoint=None)

    # Act / Assert: must not raise and must not install an exporter.
    setup_tracing(settings)


def test_inject_headers_noop_without_active_span() -> None:
    # Arrange / Act: no span active and tracing not configured.
    headers = inject_headers({})

    # Assert: no traceparent is fabricated.
    assert "traceparent" not in headers


def test_current_trace_id_none_without_active_span() -> None:
    # Assert: no valid span -> no trace id to correlate logs with.
    assert current_trace_id() is None


def test_extract_context_handles_missing_and_nonstring_headers() -> None:
    # Assert: extraction is safe with no headers and with non-string values.
    assert extract_context(None) is not None or extract_context(None) is None
    # Non-string header values (e.g. aggregateId bytes) must not raise.
    extract_context({"aggregateId": 123, "traceparent": "bad"})
