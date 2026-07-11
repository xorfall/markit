package com.markit.shared.events;

import java.util.UUID;

/**
 * Payload of a {@code scrape.requested} event. Carries what the scraper worker needs to fetch the
 * page: the {@code bookmarkId} to correlate results, the {@code ownerId} for isolation, and the
 * {@code url} to scrape (architecture §4.1).
 */
public record ScrapeRequestedPayload(UUID bookmarkId, UUID ownerId, String url) {}
