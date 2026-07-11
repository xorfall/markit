package com.markit.scraping.infrastructure.persistence;

import com.markit.scraping.application.port.ContentRepository;
import com.markit.scraping.domain.BookmarkContent;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the domain {@link ContentRepository} port. */
@Repository
public class ContentRepositoryAdapter implements ContentRepository {

  private final SpringDataBookmarkContentRepository jpa;

  ContentRepositoryAdapter(SpringDataBookmarkContentRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void save(BookmarkContent content) {
    // save() upserts by primary key (bookmark_id), so redelivery of the same content is idempotent.
    jpa.save(
        new BookmarkContentJpaEntity(
            content.bookmarkId().value(),
            content.content(),
            content.contentBytes(),
            content.scrapedAt()));
  }
}
