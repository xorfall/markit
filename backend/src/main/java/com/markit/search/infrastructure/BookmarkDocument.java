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
 * (NFR-SEC-002). Content is intentionally absent until S4.
 */
@Document(indexName = "bookmarks")
public class BookmarkDocument {

  @Id private String id;

  @Field(type = FieldType.Keyword)
  private String userId;

  @Field(type = FieldType.Keyword)
  private String categoryId;

  @Field(type = FieldType.Text)
  private String title;

  @Field(type = FieldType.Text)
  private String description;

  @Field(type = FieldType.Keyword)
  private String state;

  @Field(type = FieldType.Date, format = DateFormat.date_time)
  private Instant createdAt;

  public BookmarkDocument() {}

  public BookmarkDocument(
      String id,
      String userId,
      String categoryId,
      String title,
      String description,
      String state,
      Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.categoryId = categoryId;
    this.title = title;
    this.description = description;
    this.state = state;
    this.createdAt = createdAt;
  }

  /** Build a document from an upserted event payload. */
  public static BookmarkDocument from(BookmarkUpsertedPayload p) {
    return new BookmarkDocument(
        p.bookmarkId().toString(),
        p.ownerId().toString(),
        p.categoryId().toString(),
        p.title(),
        p.description(),
        p.state(),
        p.createdAt());
  }

  /** Build a document from a Postgres row during a full reindex (NFR-CONS-004). */
  public static BookmarkDocument from(IndexableBookmark b) {
    return new BookmarkDocument(
        b.bookmarkId().toString(),
        b.ownerId().toString(),
        b.categoryId().toString(),
        b.title(),
        b.description(),
        b.state(),
        b.createdAt());
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

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getState() {
    return state;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
