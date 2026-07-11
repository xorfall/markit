package com.markit.scraping.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for scraped content (kept separate from the domain, ADR-0001). */
@Entity
@Table(name = "bookmark_content")
public class BookmarkContentJpaEntity {

  @Id
  @Column(name = "bookmark_id")
  private UUID bookmarkId;

  @Column(nullable = false)
  private String content;

  @Column(name = "content_bytes", nullable = false)
  private int contentBytes;

  @Column(name = "scraped_at", nullable = false)
  private Instant scrapedAt;

  protected BookmarkContentJpaEntity() {}

  public BookmarkContentJpaEntity(
      UUID bookmarkId, String content, int contentBytes, Instant scrapedAt) {
    this.bookmarkId = bookmarkId;
    this.content = content;
    this.contentBytes = contentBytes;
    this.scrapedAt = scrapedAt;
  }

  public UUID getBookmarkId() {
    return bookmarkId;
  }

  public String getContent() {
    return content;
  }

  public int getContentBytes() {
    return contentBytes;
  }

  public Instant getScrapedAt() {
    return scrapedAt;
  }
}
