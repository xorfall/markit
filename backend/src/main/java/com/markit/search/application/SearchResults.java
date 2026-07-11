package com.markit.search.application;

import java.util.List;

/**
 * Outcome of a search (api-contract §5). {@code degraded}/{@code contentSearchAvailable} tell the
 * client whether results came from the full Elasticsearch content search or the Postgres
 * metadata-only fallback used when ES is down (FR-SRC-003, ADR-0007). {@code nextCursor} is an
 * opaque pagination token, {@code null} when there is no further page.
 */
public record SearchResults(
    boolean degraded,
    boolean contentSearchAvailable,
    List<SearchResultItem> results,
    String nextCursor) {

  /** A single hit: the matched bookmark, an optional highlighted {@code snippet}, and its score. */
  public record SearchResultItem(MatchedBookmark bookmark, String snippet, double score) {}
}
