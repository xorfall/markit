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
import com.markit.bookmarking.domain.BookmarkId;
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

class CollectionServiceTest {

  private final CollectionRepository collections = mock(CollectionRepository.class);
  private final CategoryRepository categories = mock(CategoryRepository.class);
  private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
  private final UserId owner = UserId.newId();

  private CollectionService service;

  @BeforeEach
  void setUp() {
    service = new CollectionService(collections, categories, bookmarks, outbox, clock);
  }

  @Test
  void should_CreateAppendedAtEnd_When_NameProvided() {
    when(collections.findByOwner(owner)).thenReturn(List.of(existing(), existing()));

    Collection created = service.create(owner, "Reading");

    ArgumentCaptor<Collection> saved = ArgumentCaptor.forClass(Collection.class);
    verify(collections).save(saved.capture());
    assertThat(saved.getValue().name()).isEqualTo("Reading");
    assertThat(saved.getValue().ownerId()).isEqualTo(owner);
    assertThat(saved.getValue().position()).isEqualTo(2);
    assertThat(created.name()).isEqualTo("Reading");
  }

  @Test
  void should_Throw404_When_RenamingCollectionNotOwned() {
    CollectionId id = CollectionId.newId();
    when(collections.findByIdAndOwner(id, owner)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rename(owner, id, "X"))
        .isInstanceOf(NotFoundException.class);
    verify(collections, never()).save(any());
  }

  @Test
  void should_Throw404_When_DeletingCollectionNotOwned() {
    CollectionId id = CollectionId.newId();
    when(collections.findByIdAndOwner(id, owner)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(owner, id)).isInstanceOf(NotFoundException.class);
    verify(collections, never()).delete(any());
  }

  @Test
  void should_AppendDeletedEventPerDescendantBookmark_When_DeletingCollection() {
    CollectionId id = CollectionId.newId();
    Collection collection = Collection.create(id, owner, "c", 0, clock.instant());
    when(collections.findByIdAndOwner(id, owner)).thenReturn(Optional.of(collection));
    CategoryId categoryId = CategoryId.newId();
    Category category = Category.create(categoryId, id, owner, "cat", 0, clock.instant());
    when(categories.findByCollectionAndOwner(id, owner)).thenReturn(List.of(category));
    Bookmark b1 = bookmark(categoryId, "https://a.example");
    Bookmark b2 = bookmark(categoryId, "https://b.example");
    when(bookmarks.findByCategoryAndOwner(categoryId, owner)).thenReturn(List.of(b1, b2));

    service.delete(owner, id);

    verify(outbox)
        .append(eq(EventTypes.AGGREGATE_BOOKMARK), eq(b1.id().value()),
            eq(EventTypes.BOOKMARK_DELETED), any());
    verify(outbox)
        .append(eq(EventTypes.AGGREGATE_BOOKMARK), eq(b2.id().value()),
            eq(EventTypes.BOOKMARK_DELETED), any());
    verify(collections).delete(collection);
  }

  private Bookmark bookmark(CategoryId categoryId, String url) {
    return Bookmark.add(BookmarkId.newId(), categoryId, owner, new Url(url), 0, clock.instant());
  }

  private Collection existing() {
    return Collection.create(CollectionId.newId(), owner, "c", 0, clock.instant());
  }
}
