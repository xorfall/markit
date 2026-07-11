package com.markit.scraping.application.port;

import com.markit.scraping.domain.BookmarkContent;

/**
 * Persistence port for scraped {@link BookmarkContent}. Implementations MUST enlist in the caller's
 * current transaction so the content row and the bookmark state transition commit atomically
 * (no dual write, ADR-0003).
 */
public interface ContentRepository {

  /** Store (upsert by bookmark id) the scraped content. Idempotent on redelivery. */
  void save(BookmarkContent content);
}
