package com.markit.search.application;

/**
 * Neutral, framework-free view of a bookmark that matched a search — the shared projection produced
 * by both the Elasticsearch path and the Postgres degraded-mode fallback, so neither ES documents
 * nor JPA entities leak past the search context (api-contract §5).
 */
public record MatchedBookmark(
    String id,
    String categoryId,
    String url,
    String title,
    String description,
    String state) {}
