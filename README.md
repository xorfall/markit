# markit

A centralized, multi-device bookmark manager with **content-aware search**: when you save a link,
markit scrapes the page and indexes its text, so you can later find a bookmark by something written
*inside* the article — not just its title or URL.

## How it works

- **PostgreSQL** is the source of truth (bookmarks, hierarchy, scraped content).
- **Elasticsearch** is a read projection for full-text search, kept in sync via a **Transactional
  Outbox** → RabbitMQ → indexer pipeline (at-least-once + idempotent = effectively-once).
- A **Python (FastAPI + Playwright)** service does the scraping (static fetch with a headless
  fallback for JS-rendered pages), decoupled over RabbitMQ.
- A **Spring Boot (Java 21)** modular monolith owns the domain, the API, and the sync pipeline.
- A **React PWA** client (web + mobile) with optimistic UI and live scrape-status via SSE.

## Architecture at a glance

```
React PWA ──REST/SSE──▶ Spring Boot backend ──▶ PostgreSQL (source of truth)
                              │  ▲                     │ outbox
                              │  └── search ── Elasticsearch ◀── indexer ◀── RabbitMQ
                              └── scrape jobs ──▶ RabbitMQ ──▶ Python scraper ──▶ web
```

## Running locally

Requires Docker.

```bash
cd deploy
docker compose up --build
```

This starts Postgres, Elasticsearch, RabbitMQ, Jaeger (tracing), Prometheus (metrics), the backend
(`:8080`), and the scraper (`:8000`).

- Backend health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/prometheus` · Traces: `http://localhost:16686`

## Development

```bash
# Backend: unit + architecture tests (no Docker)
cd backend && mvn test
# Backend: full verify incl. Testcontainers integration tests (Docker)
cd backend && mvn verify

# Scraper
cd scraper && pytest
```

## Observability

Structured JSON logs with trace ids, production-grade liveness/readiness health checks, Micrometer/
Prometheus metrics, and end-to-end OpenTelemetry tracing across the async pipeline.

## Status

Early development — walking skeleton (S0). See the codebase for slice progress.
