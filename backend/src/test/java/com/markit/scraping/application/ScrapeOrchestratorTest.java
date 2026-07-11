package com.markit.scraping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.markit.bookmarking.application.BookmarkingExceptions.NotFoundException;
import com.markit.bookmarking.application.port.BookmarkRepository;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.BookmarkId;
import com.markit.bookmarking.domain.BookmarkState;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.domain.Url;
import com.markit.identity.domain.UserId;
import com.markit.scraping.application.port.ContentRepository;
import com.markit.scraping.domain.BookmarkContent;
import com.markit.shared.events.EventTypes;
import com.markit.shared.events.ScrapeContentCompletedPayload;
import com.markit.shared.events.ScrapeFailedPayload;
import com.markit.shared.events.ScrapeMetadataReadyPayload;
import com.markit.shared.outbox.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScrapeOrchestratorTest {

  private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
  private final ContentRepository contents = mock(ContentRepository.class);
  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final org.springframework.context.ApplicationEventPublisher events =
      mock(org.springframework.context.ApplicationEventPublisher.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
  private final UserId owner = UserId.newId();

  private ScrapeOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator = new ScrapeOrchestrator(bookmarks, contents, outbox, events, clock);
  }

  private Bookmark existingBookmark(BookmarkId id) {
    Bookmark bookmark =
        Bookmark.add(id, CategoryId.newId(), owner, new Url("https://example.com"), 0, clock.instant());
    when(bookmarks.findById(id)).thenReturn(Optional.of(bookmark));
    return bookmark;
  }

  @Test
  void should_ApplyMetadataStayPendingAndEmitUpsert_When_MetadataReady() {
    BookmarkId id = BookmarkId.newId();
    Bookmark bookmark = existingBookmark(id);

    orchestrator.onMetadataReady(new ScrapeMetadataReadyPayload(id.value(), "Title", "Desc"));

    assertThat(bookmark.title()).isEqualTo("Title");
    assertThat(bookmark.description()).isEqualTo("Desc");
    assertThat(bookmark.state()).isEqualTo(BookmarkState.PENDING);
    verify(bookmarks).save(bookmark);
    verify(outbox)
        .append(
            eq(EventTypes.AGGREGATE_BOOKMARK),
            eq(id.value()),
            eq(EventTypes.BOOKMARK_UPSERTED),
            any());
  }

  @Test
  void should_StoreContentTransitionIndexedAndEmitUpsert_When_ContentCompleted() {
    BookmarkId id = BookmarkId.newId();
    Bookmark bookmark = existingBookmark(id);

    orchestrator.onContentCompleted(new ScrapeContentCompletedPayload(id.value(), "hello"));

    ArgumentCaptor<BookmarkContent> stored = ArgumentCaptor.forClass(BookmarkContent.class);
    verify(contents).save(stored.capture());
    assertThat(stored.getValue().bookmarkId()).isEqualTo(id);
    assertThat(stored.getValue().content()).isEqualTo("hello");
    assertThat(stored.getValue().contentBytes()).isEqualTo(5);
    assertThat(bookmark.state()).isEqualTo(BookmarkState.INDEXED);
    verify(bookmarks).save(bookmark);
    verify(outbox)
        .append(
            eq(EventTypes.AGGREGATE_BOOKMARK),
            eq(id.value()),
            eq(EventTypes.BOOKMARK_UPSERTED),
            any());
  }

  @Test
  void should_TransitionFailedWithReasonAndNotTouchEs_When_Failed() {
    BookmarkId id = BookmarkId.newId();
    Bookmark bookmark = existingBookmark(id);

    orchestrator.onFailed(new ScrapeFailedPayload(id.value(), "FETCH_ERROR"));

    assertThat(bookmark.state()).isEqualTo(BookmarkState.FAILED);
    assertThat(bookmark.failureReason()).isEqualTo("FETCH_ERROR");
    verify(bookmarks).save(bookmark);
    // No ES change for a failed scrape in this slice.
    verifyNoInteractions(outbox);
  }

  @Test
  void should_ResetToPendingAndEmitScrapeRequested_When_Rescrape() {
    BookmarkId id = BookmarkId.newId();
    Bookmark bookmark =
        Bookmark.add(id, CategoryId.newId(), owner, new Url("https://example.com"), 0, clock.instant());
    bookmark.markFailed("FETCH_ERROR", clock.instant());
    when(bookmarks.findByIdAndOwner(id, owner)).thenReturn(Optional.of(bookmark));

    orchestrator.rescrape(owner, id);

    assertThat(bookmark.state()).isEqualTo(BookmarkState.PENDING);
    assertThat(bookmark.failureReason()).isNull();
    verify(bookmarks).save(bookmark);
    verify(outbox)
        .append(
            eq(EventTypes.AGGREGATE_BOOKMARK),
            eq(id.value()),
            eq(EventTypes.SCRAPE_REQUESTED),
            any());
  }

  @Test
  void should_Throw404_When_RescrapingBookmarkNotOwned() {
    BookmarkId id = BookmarkId.newId();
    when(bookmarks.findByIdAndOwner(id, owner)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orchestrator.rescrape(owner, id))
        .isInstanceOf(NotFoundException.class);
    verify(bookmarks, never()).save(any());
  }

  @Test
  void should_IgnoreGracefully_When_ResultForUnknownBookmark() {
    BookmarkId id = BookmarkId.newId();
    when(bookmarks.findById(id)).thenReturn(Optional.empty());

    // No throw (would nack-loop the queue); nothing persisted or emitted.
    orchestrator.onMetadataReady(new ScrapeMetadataReadyPayload(id.value(), "Title", "Desc"));
    orchestrator.onContentCompleted(new ScrapeContentCompletedPayload(id.value(), "hello"));
    orchestrator.onFailed(new ScrapeFailedPayload(id.value(), "FETCH_ERROR"));

    verify(bookmarks, never()).save(any());
    verifyNoInteractions(contents);
    verifyNoInteractions(outbox);
  }
}
