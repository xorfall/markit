"""Unit tests for the SSRF guard (crown jewel)."""

from __future__ import annotations

import pytest

from app.scraping import ssrf
from app.scraping.ssrf import SsrfError, assert_url_allowed, is_blocked_ip


@pytest.mark.parametrize(
    "ip",
    [
        "127.0.0.1",  # loopback
        "10.0.0.1",  # RFC1918
        "192.168.1.1",  # RFC1918
        "169.254.169.254",  # cloud metadata
        "::1",  # IPv6 loopback
        "fc00::1",  # IPv6 unique-local
        "fe80::1",  # IPv6 link-local
    ],
)
def test_is_blocked_ip_blocks_unsafe_addresses(ip: str) -> None:
    # Arrange / Act / Assert
    assert is_blocked_ip(ip) is True


@pytest.mark.parametrize("ip", ["8.8.8.8", "93.184.216.34"])
def test_is_blocked_ip_allows_public_addresses(ip: str) -> None:
    # Arrange / Act / Assert
    assert is_blocked_ip(ip) is False


def test_assert_url_allowed_rejects_non_http_scheme() -> None:
    # Arrange / Act / Assert
    with pytest.raises(SsrfError):
        assert_url_allowed("file:///etc/passwd")


def test_assert_url_allowed_rejects_private_resolution(monkeypatch) -> None:
    # Arrange
    monkeypatch.setattr(ssrf, "_resolve_host", lambda host: ["10.0.0.5"])

    # Act / Assert
    with pytest.raises(SsrfError):
        assert_url_allowed("http://evil.internal.test/")


def test_assert_url_allowed_permits_public_resolution(monkeypatch) -> None:
    # Arrange
    monkeypatch.setattr(ssrf, "_resolve_host", lambda host: ["93.184.216.34"])

    # Act / Assert (should not raise)
    assert_url_allowed("http://example.com/")
