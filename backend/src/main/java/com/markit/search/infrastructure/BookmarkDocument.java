package com.markit.search.infrastructure;

import com.markit.search.application.IndexableBookmark;
import com.markit.shared.events.BookmarkUpsertedPayload;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch projection of a bookmark (index {@code bookmarks}). The document id is the
 * {@code bookmarkId}, which makes indexing idempotent: an upsert is save-by-id and a delete is
 * delete-by-id. {@code userId} is mandatory and filtered on every query for per-user isolation
 * (NFR-SEC-002).
 *
 * <p>The document is built up over two idempotent upserts (architecture §4.1): metadata first (from
 * the {@code bookmark.upserted} event), then the large scraped {@code content} — read from Postgres
 * at index time via a {@code ContentSource} rather than shipped through RabbitMQ (S5, FR-SRC-001).
 * {@code content} is {@code null} until the scrape's phase-2 has stored it.
 */
@Document(indexName = "bookmarks")
public class BookmarkDocument {

  @Id private String id;

  @Field(type = FieldType.Keyword)
  private String userId;

  @Field(type = FieldType.Keyword)
  private String categoryId;

  @Field(type = FieldType.Keyword, index = false)
  private String url;

  @Field(type = FieldType.Text)
  private String title;

  @Field(type = FieldType.Text)
  private String description;

  @Field(type = FieldType.Text)
  private String content;

  @Field(type = FieldType.Keyword)
  private String state;

  @Field(type = FieldType.Date, format = DateFormat.date_time)
  private Instant createdAt;

  public BookmarkDocument() {}

  public BookmarkDocument(
      String id,
      String userId,
      String categoryId,
      String url,
      String title,
      String description,
      String content,
      String state,
      Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.categoryId = categoryId;
    this.url = url;
    this.title = title;
    this.description = description;
    this.content = content;
    this.state = state;
    this.createdAt = createdAt;
  }

  /** Build a metadata-only document from an upserted event payload ({@code content} still null). */
  public static BookmarkDocument from(BookmarkUpsertedPayload p) {
    return new BookmarkDocument(
        p.bookmarkId().toString(),
        p.ownerId().toString(),
        p.categoryId().toString(),
        p.url(),
        p.title(),
        p.description(),
        null,
        p.state(),
        p.createdAt());
  }

  /** Build a document from a Postgres row during a full reindex (NFR-CONS-004). */
  public static BookmarkDocument from(IndexableBookmark b) {
    return new BookmarkDocument(
        b.bookmarkId().toString(),
        b.ownerId().toString(),
        b.categoryId().toString(),
        b.url(),
        b.title(),
        b.description(),
        b.content(),
        b.state(),
        b.createdAt());
  }

  /**
   * Enrich this document with the bookmark's current content (the second, idempotent upsert). A
   * {@code null} value leaves the field empty until the scrape completes.
   */
  public BookmarkDocument withContent(String content) {
    this.content = content;
    return this;
  }

  public String getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public String getUrl() {
    return url;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getContent() {
    return content;
  }

  public String getState() {
    return state;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
