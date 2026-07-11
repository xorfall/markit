package com.markit.search.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Neutral, framework-free view of a bookmark row used to (re)build the ES projection from Postgres
 * (NFR-CONS-004), decoupling the search context from the bookmarking persistence model.
 */
public record IndexableBookmark(
    UUID bookmarkId,
    UUID ownerId,
    UUID categoryId,
    String title,
    String description,
    String state,
    Instant createdAt) {}
