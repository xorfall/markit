package com.markit.search.application.port;

import com.markit.search.application.MatchedBookmark;
import java.util.List;
import java.util.UUID;

/**
 * Degraded-mode search port (FR-SRC-003, ADR-0007). When Elasticsearch is unavailable, search falls
 * back to a cheap owner-scoped, case-insensitive match over the small {@code title}/{@code
 * description} metadata fields in Postgres — never a scan over content. An adapter in the bookmarking
 * context supplies the rows; every query is mandatorily scoped to {@code userId} (NFR-SEC-002).
 */
public interface MetadataSearchSource {

  /**
   * Owner-scoped metadata search.
   *
   * @param userId the owner; every result must belong to this user (mandatory isolation)
   * @param query the raw search terms (matched against title/description, case-insensitive)
   * @param categoryId optional category filter, or {@code null} for no restriction
   * @param limit maximum results to return
   * @param offset how many leading results to skip (pagination)
   */
  List<MatchedBookmark> search(UUID userId, String query, UUID categoryId, int limit, int offset);
}
