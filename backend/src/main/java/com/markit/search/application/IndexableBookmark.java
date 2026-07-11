package com.markit.search.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Neutral, framework-free view of a bookmark row used to (re)build the ES projection from Postgres
 * (NFR-CONS-004), decoupling the search context from the bookmarking persistence model. {@code
 * content} is enriched from the {@code bookmark_content} table during a full reindex and may be
 * {@code null} until the bookmark has been scraped (S5).
 */
public record IndexableBookmark(
    UUID bookmarkId,
    UUID ownerId,
    UUID categoryId,
    String url,
    String title,
    String description,
    String content,
    String state,
    Instant createdAt) {

  /** Return a copy carrying the given scraped content (the second, idempotent enrichment). */
  public IndexableBookmark withContent(String content) {
    return new IndexableBookmark(
        bookmarkId, ownerId, categoryId, url, title, description, content, state, createdAt);
  }
}
