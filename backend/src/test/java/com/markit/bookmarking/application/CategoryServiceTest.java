package com.markit.bookmarking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.markit.bookmarking.application.BookmarkingExceptions.NotFoundException;
import com.markit.bookmarking.application.port.BookmarkRepository;
import com.markit.bookmarking.application.port.CategoryRepository;
import com.markit.bookmarking.application.port.CollectionRepository;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.Category;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.domain.Collection;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.bookmarking.domain.Url;
import com.markit.identity.domain.UserId;
import com.markit.shared.events.EventTypes;
import com.markit.shared.outbox.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CategoryServiceTest {

  private final CategoryRepository categories = mock(CategoryRepository.class);
  private final CollectionRepository collections = mock(CollectionRepository.class);
  private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
  private final UserId owner = UserId.newId();

  private CategoryService service;

  @BeforeEach
  void setUp() {
    service = new CategoryService(categories, collections, bookmarks, outbox, clock);
  }

  @Test
  void should_CreateUnderCollection_When_ParentOwnedByUser() {
    CollectionId collectionId = CollectionId.newId();
    when(collections.findByIdAndOwner(collectionId, owner))
        .thenReturn(Optional.of(collection(collectionId)));
    when(categories.findByCollectionAndOwner(collectionId, owner)).thenReturn(List.of());

    service.create(owner, collectionId, "Articles");

    ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
    verify(categories).save(saved.capture());
    assertThat(saved.getValue().name()).isEqualTo("Articles");
    assertThat(saved.getValue().collectionId()).isEqualTo(collectionId);
    assertThat(saved.getValue().position()).isZero();
  }

  @Test
  void should_Throw404_When_CreatingUnderForeignOrMissingCollection() {
    CollectionId collectionId = CollectionId.newId();
    when(collections.findByIdAndOwner(collectionId, owner)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(owner, collectionId, "Articles"))
        .isInstanceOf(NotFoundException.class);
    verify(categories, never()).save(any());
  }

  @Test
  void should_Throw404_When_RenamingCategoryNotOwned() {
    CategoryId id = CategoryId.newId();
    when(categories.findByIdAndOwner(id, owner)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rename(owner, id, "X"))
        .isInstanceOf(NotFoundException.class);
    verify(categories, never()).save(any());
  }

  @Test
  void should_AppendDeletedEventPerDescendantBookmark_When_DeletingCategory() {
    CategoryId id = CategoryId.newId();
    Category category = Category.create(id, CollectionId.newId(), owner, "c", 0, clock.instant());
    when(categories.findByIdAndOwner(id, owner)).thenReturn(Optional.of(category));
    Bookmark b1 = bookmark(id, "https://a.example");
    Bookmark b2 = bookmark(id, "https://b.example");
    when(bookmarks.findByCategoryAndOwner(id, owner)).thenReturn(List.of(b1, b2));

    service.delete(owner, id);

    verify(outbox)
        .append(eq(EventTypes.AGGREGATE_BOOKMARK), eq(b1.id().value()),
            eq(EventTypes.BOOKMARK_DELETED), any());
    verify(outbox)
        .append(eq(EventTypes.AGGREGATE_BOOKMARK), eq(b2.id().value()),
            eq(EventTypes.BOOKMARK_DELETED), any());
    verify(categories).delete(category);
  }

  private Bookmark bookmark(CategoryId categoryId, String url) {
    return Bookmark.add(
        com.markit.bookmarking.domain.BookmarkId.newId(),
        categoryId,
        owner,
        new Url(url),
        0,
        clock.instant());
  }

  private Collection collection(CollectionId id) {
    return Collection.create(id, owner, "c", 0, clock.instant());
  }
}
