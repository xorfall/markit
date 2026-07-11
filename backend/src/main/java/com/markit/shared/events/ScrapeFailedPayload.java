package com.markit.shared.events;

import java.util.UUID;

/**
 * Payload of a {@code scrape.failed} event: the machine-readable failure reason (e.g.
 * {@code CONTENT_TOO_LARGE}, {@code UNSAFE_URL}, {@code FETCH_ERROR}) surfaced on the bookmark
 * (api-contract §6, FR-SCR-005).
 */
public record ScrapeFailedPayload(UUID bookmarkId, String reason) {}
