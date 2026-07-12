package com.markit.bookmarking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.markit.bookmarking.application.BookmarkingExceptions.DuplicateUrlException;
import com.markit.bookmarking.application.BookmarkingExceptions.NotFoundException;
import com.markit.bookmarking.application.port.BookmarkRepository;
import com.markit.bookmarking.application.port.CategoryRepository;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.BookmarkId;
import com.markit.bookmarking.domain.BookmarkState;
import com.markit.bookmarking.domain.Category;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.bookmarking.domain.Url;
import com.markit.identity.domain.UserId;
import com.markit.shared.events.EventTypes;
import com.markit.shared.outbox.OutboxWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookmarkServiceTest {

  private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
  private final CategoryRepository categories = mock(CategoryRepository.class);
  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
  private final UserId owner = UserId.newId();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private BookmarkService service;

  @BeforeEach
  void setUp() {
    service = new BookmarkService(bookmarks, categories, outbox, clock, meterRegistry);
  }

  @Test
  void should_AddPendingWithTitleEqualUrl_When_CategoryOwnedAndNoDuplicate() {
    CategoryId categoryId = CategoryId.newId();
    ownedCategory(categoryId);
    when(bookmarks.existsByCategoryAndUrl(categoryId, "https://example.com")).thenReturn(false);
    when(bookmarks.findByCategoryAndOwner(categoryId, owner)).thenReturn(List.of());

    Bookmark added = service.add(owner, categoryId, "https://example.com");

    ArgumentCaptor<Bookmark> saved = ArgumentCaptor.forClass(Bookmark.class);
    verify(bookmarks).save(saved.capture());
    assertThat(saved.getValue().state()).isEqualTo(BookmarkState.PENDING);
    assertThat(saved.getValue().title()).isEqualTo("https://example.com");
    assertThat(saved.getValue().url().value()).isEqualTo("https://example.com");
    assertThat(added.ownerId()).isEqualTo(owner);
    verify(outbox)
        .append(
            eq(EventTypes.AGGREGATE_BOOKMARK),
            eq(added.id().value()),
            eq(EventTypes.BOOKMARK_UPSERTED),
            any());
    // Adding a bookmark also dispatches the initial scrape in the SAME transaction (FR-SCR-001).
    verify(outbox)
        .append(
            eq(EventTypes.AGGREGATE_BOOKMARK),
            eq(added.id().value()),
            eq(EventTypes.SCRAPE_REQUESTED),
            any());
  }

  @Test
  void should_AppendDeletedEvent_When_DeletingOwnedBookmark() {
    BookmarkId id = BookmarkId.newId();
    CategoryId categoryId = CategoryId.newId();
    Bookmark bookmark =
        Bookmark.add(id, categoryId, owner, new Url("https://example.com"), 0, clock.instant());
    when(bookmarks.findByIdAndOwner(id, owner)).thenReturn(Optional.of(bookmark));

    service.delete(owner, id);

    verify(bookmarks).delete(bookmark);
    verify(outbox)
        .append(
            eq(EventTypes.AGGREGATE_BOOKMARK),
            eq(id.value()),
            eq(EventTypes.BOOKMARK_DELETED),
            any());
  }

  @Test
  void should_NotAppendEvent_When_AddRejectedAsDuplicate() {
    CategoryId categoryId = CategoryId.newId();
    ownedCategory(categoryId);
    when(bookmarks.existsByCategoryAndUrl(categoryId, "https://example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.add(owner, categoryId, "https://example.com"))
        .isInstanceOf(DuplicateUrlException.class);
    verify(outbox, never()).append(any(), any(), any(), any());
  }

  @Test
  void should_ThrowDuplicate_When_UrlAlreadyInCategory() {
    CategoryId categoryId = CategoryId.newId();
    ownedCategory(categoryId);
    when(bookmarks.existsByCategoryAndUrl(categoryId, "https://example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.add(owner, categoryId, "https://example.com"))
        .isInstanceOf(DuplicateUrlException.class);
    verify(bookmarks, never()).save(any());
  }

  @Test
  void should_IncrementDuplicateRejectedCounter_When_AddRejectedAsDuplicate() {
    // Arrange
    CategoryId categoryId = CategoryId.newId();
    ownedCategory(categoryId);
    when(bookmarks.existsByCategoryAndUrl(categoryId, "https://example.com")).thenReturn(true);

    // Act
    assertThatThrownBy(() -> service.add(owner, categoryId, "https://example.com"))
        .isInstanceOf(DuplicateUrlException.class);

    // Assert
    assertThat(meterRegistry.counter("markit.bookmarks.duplicate_rejected").count()).isEqualTo(1.0);
    assertThat(meterRegistry.counter("markit.bookmarks.created").count()).isZero();
  }

  @Test
  void should_IncrementCreatedCounter_When_BookmarkAdded() {
    // Arrange
    CategoryId categoryId = CategoryId.newId();
    ownedCategory(categoryId);
    when(bookmarks.existsByCategoryAndUrl(categoryId, "https://example.com")).thenReturn(false);
    when(bookmarks.findByCategoryAndOwner(categoryId, owner)).thenReturn(List.of());

    // Act
    service.add(owner, categoryId, "https://example.com");

    // Assert
    assertThat(meterRegistry.counter("markit.bookmarks.created").count()).isEqualTo(1.0);
  }

  @Test
  void should_Throw404_When_AddingToForeignOrMissingCategory() {
    CategoryId categoryId = CategoryId.newId();
    when(categories.findByIdAndOwner(categoryId, owner)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.add(owner, categoryId, "https://example.com"))
        .isInstanceOf(NotFoundException.class);
    verify(bookmarks, never()).save(any());
  }

  @Test
  void should_Throw404_When_GettingBookmarkNotOwned() {
    BookmarkId id = BookmarkId.newId();
    when(bookmarks.findByIdAndOwner(id, owner)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(owner, id)).isInstanceOf(NotFoundException.class);
  }

  private void ownedCategory(CategoryId categoryId) {
    Category category =
        Category.create(categoryId, CollectionId.newId(), owner, "c", 0, clock.instant());
    when(categories.findByIdAndOwner(categoryId, owner)).thenReturn(Optional.of(category));
  }
}
