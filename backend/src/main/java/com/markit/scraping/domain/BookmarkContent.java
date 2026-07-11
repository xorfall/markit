package com.markit.scraping.domain;

import com.markit.bookmarking.domain.BookmarkId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Scraped page content for a bookmark (data-model, {@code bookmark_content} 1:1). The source of
 * truth in Postgres; the ES projection stays content-free until S5. Framework-free value object
 * (NFR-MAINT-001). {@code contentBytes} is the UTF-8 byte length, precomputed for size reporting.
 */
public record BookmarkContent(
    BookmarkId bookmarkId, String content, int contentBytes, Instant scrapedAt) {

  public BookmarkContent {
    if (bookmarkId == null || content == null || scrapedAt == null) {
      throw new IllegalArgumentException("bookmarkId, content and scrapedAt are required");
    }
  }

  /** Create content for a bookmark, deriving {@code contentBytes} from the UTF-8 encoding. */
  public static BookmarkContent of(BookmarkId bookmarkId, String content, Instant scrapedAt) {
    int bytes = content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length;
    return new BookmarkContent(bookmarkId, content, bytes, scrapedAt);
  }
}
