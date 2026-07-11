package com.markit.bookmarking.infrastructure.persistence;

import com.markit.search.application.IndexableBookmark;
import com.markit.search.application.port.BookmarkSource;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Supplies the search context with every bookmark row for a full reindex (NFR-CONS-004). Lives in
 * the bookmarking infrastructure because it owns the persistence model; exposes only the neutral
 * {@link IndexableBookmark} view.
 */
@Component
public class BookmarkSourceAdapter implements BookmarkSource {

  private final SpringDataBookmarkRepository jpa;

  BookmarkSourceAdapter(SpringDataBookmarkRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<IndexableBookmark> findAllForIndexing() {
    return jpa.findAll().stream()
        .map(
            e ->
                new IndexableBookmark(
                    e.getId(),
                    e.getOwnerId(),
                    e.getCategoryId(),
                    e.getTitle(),
                    e.getDescription(),
                    e.getState(),
                    e.getCreatedAt()))
        .toList();
  }
}
