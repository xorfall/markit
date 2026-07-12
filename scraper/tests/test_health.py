"""Tests for the S0 HTTP surface: identity + liveness + readiness."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app import __version__
from app.main import create_app


@pytest.fixture()
def client() -> TestClient:
    """Provide a TestClient bound to a fresh app instance."""
    return TestClient(create_app())


def test_root_returns_service_identity(client: TestClient) -> None:
    # Arrange / Act
    response = client.get("/")

    # Assert
    assert response.status_code == 200
    assert response.json() == {
        "service": "markit-scraper",
        "version": __version__,
    }


def test_health_reports_ok(client: TestClient) -> None:
    # Arrange / Act
    response = client.get("/health")

    # Assert
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_readiness_reports_ready(client: TestClient) -> None:
    # Arrange / Act
    response = client.get("/healthz/ready")

    # Assert
    assert response.status_code == 200
    assert response.json() == {"status": "ready"}


def test_metrics_endpoint_exposes_scrape_metrics(client: TestClient) -> None:
    # Arrange / Act
    response = client.get("/metrics")

    # Assert
    assert response.status_code == 200
    assert "markit_scrape_total" in response.text
