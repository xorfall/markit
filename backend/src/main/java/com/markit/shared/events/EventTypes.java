package com.markit.shared.events;

/**
 * Shared event contract: routing keys and aggregate types exchanged between the bookmarking producer
 * and the search-index consumer via the transactional outbox (ADR-0003). Kept in one place so both
 * sides agree on the wire vocabulary.
 */
public final class EventTypes {

  /** Aggregate type recorded on every bookmark outbox row. */
  public static final String AGGREGATE_BOOKMARK = "bookmark";

  /** A bookmark was added, edited or moved: upsert the ES projection by id. */
  public static final String BOOKMARK_UPSERTED = "bookmark.upserted";

  /** A bookmark was deleted (directly or by cascade): delete the ES projection by id. */
  public static final String BOOKMARK_DELETED = "bookmark.deleted";

  private EventTypes() {}
}
