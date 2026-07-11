package com.markit.search.presentation;

import com.markit.search.application.MatchedBookmark;
import com.markit.search.application.SearchResults;
import java.util.List;

/** Response payloads for the Search API (api-contract §5). Search-owned; no ES/JPA types leak out. */
public final class SearchDtos {

  private SearchDtos() {}

  /** The subset of the Bookmark DTO returned on a search hit (api-contract §5). */
  public record BookmarkResult(
      String id,
      String categoryId,
      String url,
      String title,
      String description,
      String state) {

    static BookmarkResult from(MatchedBookmark b) {
      return new BookmarkResult(
          b.id(), b.categoryId(), b.url(), b.title(), b.description(), b.state());
    }
  }

  /** A single hit: the bookmark, an optional highlighted {@code snippet}, and its relevance score. */
  public record ResultItem(BookmarkResult bookmark, String snippet, double score) {}

  /**
   * Search response. In degraded mode (ES down) {@code degraded} is {@code true},
   * {@code contentSearchAvailable} is {@code false}, and hits carry no content snippet (ADR-0007).
   */
  public record SearchResponse(
      boolean degraded,
      boolean contentSearchAvailable,
      List<ResultItem> results,
      String nextCursor) {

    public static SearchResponse from(SearchResults results) {
      List<ResultItem> items =
          results.results().stream()
              .map(
                  r ->
                      new ResultItem(
                          BookmarkResult.from(r.bookmark()), r.snippet(), r.score()))
              .toList();
      return new SearchResponse(
          results.degraded(), results.contentSearchAvailable(), items, results.nextCursor());
    }
  }
}
