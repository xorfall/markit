package com.markit.scraping.application;

import com.markit.bookmarking.application.BookmarkingExceptions.NotFoundException;
import com.markit.bookmarking.application.port.BookmarkRepository;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.BookmarkId;
import com.markit.identity.domain.UserId;
import com.markit.scraping.application.port.ContentRepository;
import com.markit.scraping.domain.BookmarkContent;
import com.markit.shared.events.BookmarkUpsertedPayload;
import com.markit.shared.events.EventTypes;
import com.markit.shared.events.ScrapeContentCompletedPayload;
import com.markit.shared.events.ScrapeFailedPayload;
import com.markit.shared.events.ScrapeMetadataReadyPayload;
import com.markit.shared.events.ScrapeRequestedPayload;
import com.markit.shared.outbox.OutboxWriter;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the two-phase async scrape (architecture §4.1, FR-SCR-001..005). Dispatch and every
 * result-consumption path run in their own transaction so the outbox event and the state change
 * commit atomically (no dual write, ADR-0003). Result handlers consume trusted internal system
 * events — they load the bookmark by id (NOT owner-scoped) and, if it is gone, log and return rather
 * than throw, so a stale event cannot nack-loop the queue.
 */
@Service
public class ScrapeOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(ScrapeOrchestrator.class);

  private final BookmarkRepository bookmarks;
  private final ContentRepository contents;
  private final OutboxWriter outbox;
  private final Clock clock;

  public ScrapeOrchestrator(
      BookmarkRepository bookmarks,
      ContentRepository contents,
      OutboxWriter outbox,
      Clock clock) {
    this.bookmarks = bookmarks;
    this.contents = contents;
    this.outbox = outbox;
    this.clock = clock;
  }

  /** Dispatch the initial scrape for a freshly added bookmark (same-tx, FR-SCR-001). */
  @Transactional
  public void requestScrape(Bookmark bookmark) {
    appendScrapeRequested(bookmark);
  }

  /**
   * Owner-scoped manual re-scrape (FR-SCR-004): reset to {@code PENDING}, refresh the projection and
   * re-dispatch. Throws {@link NotFoundException} (404) if the bookmark is not owned by the caller.
   */
  @Transactional
  public void rescrape(UserId owner, BookmarkId id) {
    Bookmark bookmark =
        bookmarks
            .findByIdAndOwner(id, owner)
            .orElseThrow(() -> new NotFoundException("Bookmark not found"));
    bookmark.resetToPending(clock.instant());
    bookmarks.save(bookmark);
    appendUpserted(bookmark);
    appendScrapeRequested(bookmark);
  }

  /** First phase result: apply scraped title/description; the bookmark stays {@code PENDING}. */
  @Transactional
  public void onMetadataReady(ScrapeMetadataReadyPayload payload) {
    load(payload.bookmarkId())
        .ifPresent(
            bookmark -> {
              bookmark.applyMetadata(payload.title(), payload.description(), clock.instant());
              bookmarks.save(bookmark);
              appendUpserted(bookmark);
            });
  }

  /** Second phase result: persist the content, transition to {@code INDEXED} and refresh ES. */
  @Transactional
  public void onContentCompleted(ScrapeContentCompletedPayload payload) {
    load(payload.bookmarkId())
        .ifPresent(
            bookmark -> {
              contents.save(
                  BookmarkContent.of(bookmark.id(), payload.content(), clock.instant()));
              bookmark.markIndexed(clock.instant());
              bookmarks.save(bookmark);
              appendUpserted(bookmark);
            });
  }

  /** Failure result: transition to {@code FAILED} with the reason. No ES change in this slice. */
  @Transactional
  public void onFailed(ScrapeFailedPayload payload) {
    load(payload.bookmarkId())
        .ifPresent(
            bookmark -> {
              bookmark.markFailed(payload.reason(), clock.instant());
              bookmarks.save(bookmark);
            });
  }

  private Optional<Bookmark> load(java.util.UUID bookmarkId) {
    Optional<Bookmark> bookmark = bookmarks.findById(BookmarkId.of(bookmarkId));
    if (bookmark.isEmpty()) {
      // Stale/unknown result (e.g. the bookmark was deleted mid-scrape): ignore, do not nack-loop.
      log.warn("Ignoring scrape result for unknown bookmark {}", bookmarkId);
    }
    return bookmark;
  }

  private void appendScrapeRequested(Bookmark bookmark) {
    outbox.append(
        EventTypes.AGGREGATE_BOOKMARK,
        bookmark.id().value(),
        EventTypes.SCRAPE_REQUESTED,
        new ScrapeRequestedPayload(
            bookmark.id().value(), bookmark.ownerId().value(), bookmark.url().value()));
  }

  private void appendUpserted(Bookmark bookmark) {
    outbox.append(
        EventTypes.AGGREGATE_BOOKMARK,
        bookmark.id().value(),
        EventTypes.BOOKMARK_UPSERTED,
        new BookmarkUpsertedPayload(
            bookmark.id().value(),
            bookmark.ownerId().value(),
            bookmark.categoryId().value(),
            bookmark.url().value(),
            bookmark.title(),
            bookmark.description(),
            bookmark.state().name(),
            bookmark.createdAt()));
  }
}
