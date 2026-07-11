package com.markit.shared.events;

import java.util.UUID;

/**
 * Payload of a {@code scrape.content-completed} event: the full extracted page content. Stored in
 * Postgres as the source of truth and transitions the bookmark to {@code INDEXED} (architecture §4.1).
 */
public record ScrapeContentCompletedPayload(UUID bookmarkId, String content) {}
