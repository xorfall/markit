package com.markit.shared.events;

import java.util.UUID;

/**
 * Payload of a {@code scrape.metadata-ready} event: the title and description extracted in the first
 * scrape phase. Applied to the bookmark while it stays {@code PENDING} (architecture §4.1).
 */
public record ScrapeMetadataReadyPayload(UUID bookmarkId, String title, String description) {}
