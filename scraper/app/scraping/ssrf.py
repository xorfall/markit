"""SSRF (Server-Side Request Forgery) guard.

The scrape service fetches **user-supplied URLs** from inside the deployment
network, which is a classic SSRF surface (ADR-0008, FR-SCR-006, R-SEC-02). This
module is the crown-jewel guard: it is intentionally pure and side-effect free
(apart from DNS resolution behind a monkeypatchable seam) so it can be unit
tested exhaustively.

Defenses implemented here:

* **Scheme allowlist** — only ``http`` / ``https`` are accepted.
* **Resolve-and-check** — the hostname is resolved to concrete IP addresses and
  every resolved address is validated against a deny-list. Checking the resolved
  IP (not just the hostname) guards against DNS-rebinding.
* **Redirect re-validation** — :func:`validate_redirect` re-runs the full check
  on every redirect hop's target.
* **Per-sub-request check** — the same primitives (:func:`assert_url_allowed`,
  :func:`is_blocked_ip`) are re-applied to every headless sub-request (ADR-0009).
"""

from __future__ import annotations

import ipaddress
import socket
from urllib.parse import urljoin, urlsplit

__all__ = [
    "SsrfError",
    "is_blocked_ip",
    "assert_url_allowed",
    "validate_redirect",
]

# Only these URL schemes may ever be fetched (ADR-0008 §1).
_ALLOWED_SCHEMES = frozenset({"http", "https"})

# The cloud-metadata endpoint is called out explicitly even though it also falls
# under the link-local range; being explicit documents intent (R-SEC-02).
_CLOUD_METADATA_IPV4 = ipaddress.ip_address("169.254.169.254")


class SsrfError(Exception):
    """Raised when a URL or resolved address is refused by the SSRF guard."""


def is_blocked_ip(ip: str) -> bool:
    """Return ``True`` when an IP address must not be contacted.

    Blocks loopback, private (RFC 1918 / unique-local ``fc00::/7``), link-local
    (``169.254.0.0/16``, ``fe80::/10``), multicast, reserved, unspecified, and
    the cloud-metadata address ``169.254.169.254``. Any address that is not
    globally routable is blocked as a fail-closed default.

    Args:
        ip: A textual IPv4 or IPv6 address.

    Returns:
        ``True`` if the address is unsafe to contact, ``False`` otherwise.
    """
    try:
        address = ipaddress.ip_address(ip)
    except ValueError:
        # Fail closed: an address we cannot parse is treated as unsafe.
        return True

    return (
        address == _CLOUD_METADATA_IPV4
        or address.is_loopback
        or address.is_private
        or address.is_link_local
        or address.is_multicast
        or address.is_reserved
        or address.is_unspecified
        or not address.is_global
    )


def _resolve_host(host: str) -> list[str]:
    """Resolve ``host`` to the list of its IP addresses.

    This is the single DNS seam for the module; tests monkeypatch it to avoid
    real network access and to simulate rebinding.

    Args:
        host: A hostname or IP literal.

    Returns:
        The resolved textual IP addresses.

    Raises:
        SsrfError: If the hostname cannot be resolved.
    """
    try:
        infos = socket.getaddrinfo(host, None, proto=socket.IPPROTO_TCP)
    except socket.gaierror as exc:
        raise SsrfError(f"could not resolve host: {host}") from exc
    return [info[4][0] for info in infos]


def assert_url_allowed(url: str) -> None:
    """Validate that ``url`` is safe to fetch, raising otherwise.

    Enforces the scheme allowlist and the resolve-and-check deny-list: the
    hostname is resolved and the request is refused if **any** resolved address
    is blocked.

    Args:
        url: The absolute URL about to be fetched.

    Raises:
        SsrfError: If the scheme is not http(s), the host is missing, resolution
            fails, or any resolved address is on the deny-list.
    """
    parts = urlsplit(url)

    if parts.scheme.lower() not in _ALLOWED_SCHEMES:
        raise SsrfError(f"scheme not allowed: {parts.scheme!r}")

    host = parts.hostname
    if not host:
        raise SsrfError(f"missing host in url: {url!r}")

    resolved = _resolve_host(host)
    if not resolved:
        raise SsrfError(f"host resolved to no addresses: {host}")

    for ip in resolved:
        if is_blocked_ip(ip):
            raise SsrfError(f"blocked address for host {host}: {ip}")


def validate_redirect(base_url: str, location: str) -> str:
    """Resolve and validate a redirect ``location`` against ``base_url``.

    The ``Location`` header may be relative; it is joined against the previous
    hop and the resulting absolute URL is re-validated with the full deny-list
    (ADR-0008 §3 — no redirect into a private range).

    Args:
        base_url: The URL of the hop that returned the redirect.
        location: The value of the ``Location`` response header.

    Returns:
        The absolute, validated target URL.

    Raises:
        SsrfError: If the redirect target is unsafe.
    """
    target = urljoin(base_url, location)
    assert_url_allowed(target)
    return target
