package com.markit.scraping.infrastructure.persistence;

import com.markit.search.application.port.ContentSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Supplies the search context with a bookmark's scraped page text for ES enrichment (S5,
 * architecture §4.1). Lives in the scraping infrastructure because it owns the {@code
 * bookmark_content} persistence model; exposes only a neutral {@code Optional<String>}. Empty until
 * the scrape's content phase has stored a row.
 */
@Component
public class ContentSourceAdapter implements ContentSource {

  private final SpringDataBookmarkContentRepository jpa;

  ContentSourceAdapter(SpringDataBookmarkContentRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<String> findContent(UUID bookmarkId) {
    return jpa.findById(bookmarkId).map(BookmarkContentJpaEntity::getContent);
  }
}
