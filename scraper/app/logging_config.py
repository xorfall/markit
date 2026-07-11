"""Structured JSON logging configuration.

Configures ``structlog`` to emit one JSON object per log line to stdout. Every
record carries a ``trace_id`` field (a placeholder correlation id for now) so
logs can later be correlated with OpenTelemetry traces and cross-service
messages.
"""

from __future__ import annotations

import logging
import sys
from contextvars import ContextVar

import structlog

# Correlation id propagated across an async request/message handling scope.
# In later phases this is populated from an incoming trace header or the active
# OpenTelemetry span context; for S0 it defaults to a static placeholder.
_trace_id_var: ContextVar[str] = ContextVar("trace_id", default="-")


def bind_trace_id(trace_id: str) -> None:
    """Bind a correlation id for the current context.

    Args:
        trace_id: Identifier to attach to every subsequent log record in the
            current execution context.
    """
    _trace_id_var.set(trace_id)


def _add_trace_id(
    _logger: object, _method_name: str, event_dict: dict[str, object]
) -> dict[str, object]:
    """structlog processor that injects the current ``trace_id``."""
    event_dict.setdefault("trace_id", _trace_id_var.get())
    return event_dict


def configure_logging(log_level: str = "INFO") -> None:
    """Configure structured JSON logging to stdout.

    Wires the standard library logging module and ``structlog`` so both emit
    the same JSON format. Safe to call once during application startup.

    Args:
        log_level: Root log level name (e.g. ``"INFO"``, ``"DEBUG"``).
    """
    level = logging.getLevelNamesMapping().get(log_level.upper(), logging.INFO)

    logging.basicConfig(
        format="%(message)s",
        stream=sys.stdout,
        level=level,
        force=True,
    )

    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            _add_trace_id,
            structlog.processors.TimeStamper(fmt="iso", utc=True),
            structlog.processors.StackInfoRenderer(),
            structlog.processors.format_exc_info,
            structlog.processors.JSONRenderer(),
        ],
        wrapper_class=structlog.make_filtering_bound_logger(level),
        logger_factory=structlog.PrintLoggerFactory(),
        cache_logger_on_first_use=True,
    )


def get_logger(name: str | None = None) -> structlog.stdlib.BoundLogger:
    """Return a bound structlog logger.

    Args:
        name: Optional logger name (typically ``__name__``).
    """
    return structlog.get_logger(name)
