"""Application configuration.

Settings are loaded from environment variables (and an optional ``.env`` file)
using ``pydantic-settings``. All values have sensible localhost defaults so the
service boots in development without extra configuration.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

# Default maximum size (in bytes) of a fetched response body: 1 MiB.
# The fetcher enforces this cap while streaming to bound memory usage and to
# defend against decompression bombs (R-SEC-05).
DEFAULT_MAX_CONTENT_BYTES = 1_048_576

# Default bounded timeout (seconds) for an outbound fetch.
DEFAULT_REQUEST_TIMEOUT_SECONDS = 15.0

# Default maximum number of redirect hops followed (each re-validated for SSRF).
DEFAULT_MAX_REDIRECTS = 5

# Default minimum length (characters) of extracted content below which the
# static extraction is considered insufficient and the headless fallback runs.
DEFAULT_MIN_CONTENT_LENGTH = 500


class Settings(BaseSettings):
    """Runtime configuration for the scrape service.

    Attributes:
        rabbitmq_host: Hostname of the RabbitMQ broker.
        rabbitmq_port: AMQP port of the RabbitMQ broker.
        rabbitmq_user: Username used to authenticate against RabbitMQ.
        rabbitmq_password: Password used to authenticate against RabbitMQ.
        rabbitmq_vhost: Virtual host on the RabbitMQ broker.
        max_content_bytes: Hard cap on fetched (decoded) response body size in
            bytes; also enforced against the final extracted content.
        request_timeout_seconds: Bounded timeout for an outbound fetch.
        max_redirects: Maximum redirect hops followed, each re-validated for SSRF.
        min_content_length: Minimum static-extraction content length below which
            the headless-browser fallback is triggered.
        otlp_endpoint: Optional OTLP collector endpoint for OpenTelemetry
            traces. When unset, tracing export stays disabled.
        log_level: Root log level for the JSON logging configuration.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    rabbitmq_host: str = Field(default="localhost")
    rabbitmq_port: int = Field(default=5672)
    rabbitmq_user: str = Field(default="guest")
    rabbitmq_password: str = Field(default="guest")
    rabbitmq_vhost: str = Field(default="/")

    max_content_bytes: int = Field(default=DEFAULT_MAX_CONTENT_BYTES)
    request_timeout_seconds: float = Field(default=DEFAULT_REQUEST_TIMEOUT_SECONDS)
    max_redirects: int = Field(default=DEFAULT_MAX_REDIRECTS)
    min_content_length: int = Field(default=DEFAULT_MIN_CONTENT_LENGTH)

    otlp_endpoint: str | None = Field(default=None)

    log_level: str = Field(default="INFO")


@lru_cache
def get_settings() -> Settings:
    """Return a cached ``Settings`` instance.

    Using an ``lru_cache`` guarantees a single settings object per process,
    which callers can depend on without re-parsing the environment.
    """
    return Settings()
