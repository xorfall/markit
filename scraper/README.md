# markit-scraper

Python scrape service for the **markit** project. It fetches user-supplied URLs
and extracts their content, communicating with the rest of the system over
RabbitMQ.

This is the **S0 walking skeleton**: a clean, bootable FastAPI service with the
right structure and health endpoints. It contains **no scraping logic yet** —
the fetch/extraction/messaging modules are clearly-marked placeholders.

## Layout

```
scraper/
├── app/
│   ├── main.py            # FastAPI app factory + HTTP routes
│   ├── config.py          # pydantic-settings Settings (env-driven)
│   ├── logging_config.py  # structured JSON logging to stdout
│   ├── tracing.py         # OpenTelemetry setup (opt-in placeholder)
│   ├── scraping/          # SSRF guard + extraction (placeholders)
│   └── messaging/         # RabbitMQ consumer/publisher (placeholder)
├── tests/                 # pytest + FastAPI TestClient
├── Dockerfile             # Playwright Python base image
└── pyproject.toml
```

## Endpoints

| Method | Path              | Purpose                                          |
| ------ | ----------------- | ------------------------------------------------ |
| GET    | `/`               | Service identity (`{"service","version"}`)       |
| GET    | `/health`         | Liveness — always 200 while the process is up    |
| GET    | `/healthz/ready`  | Readiness — 200 `{"status":"ready"}`             |

## Run locally

Requires Python 3.12+.

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'

uvicorn app.main:app --reload
```

The service listens on `http://127.0.0.1:8000`.

## Test

```bash
pytest
```

## Configuration

All settings have localhost defaults (see `app/config.py`). Override via env:

| Variable            | Default     | Notes                                  |
| ------------------- | ----------- | -------------------------------------- |
| `RABBITMQ_HOST`     | `localhost` |                                        |
| `RABBITMQ_PORT`     | `5672`      |                                        |
| `RABBITMQ_USER`     | `guest`     |                                        |
| `RABBITMQ_PASSWORD` | `guest`     |                                        |
| `MAX_CONTENT_BYTES` | `1048576`   | Fetched-body cap (1 MiB)               |
| `OTLP_ENDPOINT`     | _(unset)_   | Enables tracing opt-in when set        |
| `LOG_LEVEL`         | `INFO`      |                                        |

## Scope: S0 vs. later

**In S0 (this skeleton):**

- Bootable FastAPI service, app factory, routers.
- Structured JSON logging with a correlation-id placeholder.
- Liveness + readiness endpoints (readiness is a static `ready` for now).
- Env-driven configuration.
- Placeholder modules documenting future behaviour.

**Later phases (not implemented here):**

- URL fetching with an **SSRF guard**: deny private/loopback/link-local/metadata
  IPs, resolve-and-check, re-validate every redirect, scheme + content-type
  allowlist, per-sub-request checks for the headless path, and a ≤1 MiB body cap.
- **Two-phase extraction**: fast metadata, then full content via `trafilatura`
  with a **Playwright** headless Chromium fallback.
- **RabbitMQ** integration: consume `scrape-requested`, publish `metadata-ready`
  then `content-completed`; readiness probe checks the broker connection.
- **OpenTelemetry** tracing wired to an OTLP collector.
