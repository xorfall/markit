"""Two-phase content extraction (placeholder for S0).

Planned extraction pipeline:

Phase 1 - fast metadata
    Parse the fetched HTML for lightweight metadata (title, description,
    canonical URL, Open Graph / Twitter card tags, language, publish date).
    This phase is cheap and is emitted first as a ``metadata-ready`` event so
    consumers get an immediate preview.

Phase 2 - full content
    Extract the main article text with ``trafilatura`` (static extraction on
    the already-fetched HTML). If static extraction yields too little content
    (JS-rendered pages, paywalled/lazy content), fall back to a Playwright
    headless Chromium render, then re-run extraction on the rendered DOM.
    The completed result is emitted as a ``content-completed`` event.

Neither ``trafilatura`` nor ``playwright`` is invoked in S0; they are declared
as dependencies only. No logic is implemented here yet.
"""

from __future__ import annotations
