"""SSRF (Server-Side Request Forgery) guard (placeholder for S0).

This module will protect the fetcher from being coerced into requesting
internal or otherwise unsafe resources. Planned behaviour:

Deny-list enforcement
    Reject resolved IP addresses that are private (RFC 1918), loopback,
    link-local, unique-local, or belong to cloud metadata ranges
    (e.g. 169.254.169.254). Reject non-global / reserved addresses generally.

Scheme + content-type allowlist
    Only allow ``http`` / ``https`` schemes. After response headers arrive,
    only allow an allowlisted set of content types (HTML / text). Enforce the
    ``MAX_CONTENT_BYTES`` (<= 1 MiB) cap while streaming the body.

Resolve-and-check (TOCTOU-safe)
    Resolve the hostname to concrete IP(s), validate every resolved address
    against the deny-list, and connect to the validated address so DNS cannot
    be rebound between the check and the connect.

Redirect re-validation
    Follow redirects manually, re-running the full deny-list + scheme check on
    every hop's target before dispatching the next request.

Per-sub-request check for headless fallback
    When the Playwright headless browser is used, intercept and validate every
    sub-resource request (documents, XHR, images, ...) with the same deny-list,
    so an attacker-controlled page cannot pivot to internal hosts.

No logic is implemented in S0.
"""

from __future__ import annotations
