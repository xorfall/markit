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
# In later phases the fetcher enforces this cap to bound memory usage.
DEFAULT_MAX_CONTENT_BYTES = 1_048_576


class Settings(BaseSettings):
    """Runtime configuration for the scrape service.

    Attributes:
        rabbitmq_host: Hostname of the RabbitMQ broker.
        rabbitmq_port: AMQP port of the RabbitMQ broker.
        rabbitmq_user: Username used to authenticate against RabbitMQ.
        rabbitmq_password: Password used to authenticate against RabbitMQ.
        max_content_bytes: Hard cap on fetched response body size in bytes.
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

    max_content_bytes: int = Field(default=DEFAULT_MAX_CONTENT_BYTES)

    otlp_endpoint: str | None = Field(default=None)

    log_level: str = Field(default="INFO")


@lru_cache
def get_settings() -> Settings:
    """Return a cached ``Settings`` instance.

    Using an ``lru_cache`` guarantees a single settings object per process,
    which callers can depend on without re-parsing the environment.
    """
    return Settings()
