package com.markit.shared.events;

import java.util.UUID;

/**
 * Payload of a {@code bookmark.deleted} event. Only the id is needed: the consumer deletes the ES
 * doc by id, which is idempotent (deleting an already-absent doc is a no-op).
 */
public record BookmarkDeletedPayload(UUID bookmarkId) {}
