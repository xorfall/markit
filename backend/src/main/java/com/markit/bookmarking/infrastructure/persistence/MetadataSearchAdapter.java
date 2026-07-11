package com.markit.bookmarking.infrastructure.persistence;

import com.markit.search.application.MatchedBookmark;
import com.markit.search.application.port.MetadataSearchSource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Postgres degraded-mode search (FR-SRC-003, ADR-0007), implemented in the bookmarking
 * infrastructure because it owns the persistence model. Owner-scoped, case-insensitive match over
 * the small {@code title}/{@code description} metadata — never a scan over content — exposing only
 * the neutral {@link MatchedBookmark} view.
 */
@Component
public class MetadataSearchAdapter implements MetadataSearchSource {

  private final SpringDataBookmarkRepository jpa;

  MetadataSearchAdapter(SpringDataBookmarkRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<MatchedBookmark> search(
      UUID userId, String query, UUID categoryId, int limit, int offset) {
    String term = query == null ? "" : query.trim();
    int size = Math.max(limit, 1);
    int page = offset / size;
    return jpa
        .searchMetadata(userId, categoryId, term, PageRequest.of(page, size))
        .stream()
        .map(
            e ->
                new MatchedBookmark(
                    e.getId().toString(),
                    e.getCategoryId().toString(),
                    e.getUrl(),
                    e.getTitle(),
                    e.getDescription(),
                    e.getState()))
        .toList();
  }
}
