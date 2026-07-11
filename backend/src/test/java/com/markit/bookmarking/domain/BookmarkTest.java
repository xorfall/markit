package com.markit.bookmarking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.markit.identity.domain.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookmarkTest {

  private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
  private static final Instant LATER = Instant.parse("2026-07-11T01:00:00Z");

  private Bookmark newBookmark() {
    return Bookmark.add(
        BookmarkId.newId(),
        CategoryId.newId(),
        UserId.newId(),
        new Url("https://example.com"),
        0,
        NOW);
  }

  @Test
  void should_KeepPendingAndSetTitleDescription_When_ApplyMetadata() {
    Bookmark bookmark = newBookmark();

    bookmark.applyMetadata("Scraped Title", "Scraped description", LATER);

    assertThat(bookmark.state()).isEqualTo(BookmarkState.PENDING);
    assertThat(bookmark.title()).isEqualTo("Scraped Title");
    assertThat(bookmark.description()).isEqualTo("Scraped description");
    assertThat(bookmark.updatedAt()).isEqualTo(LATER);
  }

  @Test
  void should_KeepUrlTitle_When_ApplyMetadataWithBlankTitle() {
    Bookmark bookmark = newBookmark();

    bookmark.applyMetadata("   ", null, LATER);

    assertThat(bookmark.title()).isEqualTo("https://example.com");
  }

  @Test
  void should_TransitionToIndexed_When_MarkIndexed() {
    Bookmark bookmark = newBookmark();

    bookmark.markIndexed(LATER);

    assertThat(bookmark.state()).isEqualTo(BookmarkState.INDEXED);
    assertThat(bookmark.failureReason()).isNull();
  }

  @Test
  void should_TransitionToFailedWithReason_When_MarkFailed() {
    Bookmark bookmark = newBookmark();

    bookmark.markFailed("FETCH_ERROR", LATER);

    assertThat(bookmark.state()).isEqualTo(BookmarkState.FAILED);
    assertThat(bookmark.failureReason()).isEqualTo("FETCH_ERROR");
  }

  @Test
  void should_ClearFailureAndReturnToPending_When_ResetToPending() {
    Bookmark bookmark = newBookmark();
    bookmark.markFailed("UNSAFE_URL", NOW);

    bookmark.resetToPending(LATER);

    assertThat(bookmark.state()).isEqualTo(BookmarkState.PENDING);
    assertThat(bookmark.failureReason()).isNull();
    assertThat(bookmark.updatedAt()).isEqualTo(LATER);
  }
}
