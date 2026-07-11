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

  /**
   * A scrape was requested for a bookmark (on add or manual rescrape). Consumed by the Python
   * scraper worker off the scrape-requests queue (architecture §4.1). Uses {@code bookmarkId} as the
   * aggregate id so it stays ordered with the bookmark's other events on replay.
   */
  public static final String SCRAPE_REQUESTED = "scrape.requested";

  /** First scrape phase completed: title/description are ready (bookmark stays {@code PENDING}). */
  public static final String SCRAPE_METADATA_READY = "scrape.metadata-ready";

  /** Second scrape phase completed: the page content is ready (bookmark becomes {@code INDEXED}). */
  public static final String SCRAPE_CONTENT_COMPLETED = "scrape.content-completed";

  /** A scrape failed (unsafe URL, content too large, fetch error): the bookmark becomes {@code FAILED}. */
  public static final String SCRAPE_FAILED = "scrape.failed";

  private EventTypes() {}
}
