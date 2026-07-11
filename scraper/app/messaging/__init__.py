"""Messaging subsystem (placeholder for S0).

This package will hold the RabbitMQ integration that drives the scrape service
asynchronously. Planned flow:

Consumer
    Subscribe to the ``scrape-requested`` queue. Each message carries a scrape
    job (request id + user-supplied URL). The consumer runs the fetch +
    extraction pipeline for that job.

Publisher
    Emit two events per job as work progresses:
      1. ``metadata-ready``    - after phase 1 (fast metadata) completes.
      2. ``content-completed`` - after phase 2 (full content) completes.

Also responsible for connection lifecycle (used by the ``/healthz/ready``
readiness probe), acknowledgements, and dead-lettering on failure.

No broker connection or handler is implemented in S0.
"""

from __future__ import annotations
