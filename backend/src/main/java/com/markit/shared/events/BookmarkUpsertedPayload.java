package com.markit.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of a {@code bookmark.upserted} event. Carries only the projection-relevant fields (no
 * scraped content — that arrives in S4). {@code userId} is mandatory so the ES doc can enforce
 * per-user isolation on every query (NFR-SEC-002).
 */
public record BookmarkUpsertedPayload(
    UUID bookmarkId,
    UUID ownerId,
    UUID categoryId,
    String url,
    String title,
    String description,
    String state,
    Instant createdAt) {}
