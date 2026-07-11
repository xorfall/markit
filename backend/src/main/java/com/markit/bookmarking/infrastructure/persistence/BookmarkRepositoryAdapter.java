package com.markit.bookmarking.infrastructure.persistence;

import com.markit.bookmarking.application.port.BookmarkRepository;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.BookmarkId;
import com.markit.bookmarking.domain.BookmarkState;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.domain.Url;
import com.markit.identity.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the domain {@link BookmarkRepository} port. */
@Repository
public class BookmarkRepositoryAdapter implements BookmarkRepository {

  private final SpringDataBookmarkRepository jpa;

  BookmarkRepositoryAdapter(SpringDataBookmarkRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Bookmark> findByIdAndOwner(BookmarkId id, UserId owner) {
    return jpa.findByIdAndOwnerId(id.value(), owner.value())
        .map(BookmarkRepositoryAdapter::toDomain);
  }

  @Override
  public List<Bookmark> findByCategoryAndOwner(CategoryId categoryId, UserId owner) {
    return jpa
        .findByCategoryIdAndOwnerIdOrderByPositionAsc(categoryId.value(), owner.value())
        .stream()
        .map(BookmarkRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByCategoryAndUrl(CategoryId categoryId, String url) {
    return jpa.existsByCategoryIdAndUrl(categoryId.value(), url);
  }

  @Override
  public void save(Bookmark bookmark) {
    // Flush eagerly so a UNIQUE(category_id, url) violation surfaces here (race-safe duplicate
    // handling in BookmarkService), not later at transaction commit.
    jpa.saveAndFlush(
        new BookmarkJpaEntity(
            bookmark.id().value(),
            bookmark.categoryId().value(),
            bookmark.ownerId().value(),
            bookmark.url().value(),
            bookmark.title(),
            bookmark.description(),
            bookmark.state().name(),
            bookmark.position(),
            bookmark.createdAt(),
            bookmark.updatedAt()));
  }

  @Override
  public void delete(Bookmark bookmark) {
    jpa.deleteById(bookmark.id().value());
  }

  private static Bookmark toDomain(BookmarkJpaEntity e) {
    return Bookmark.rehydrate(
        BookmarkId.of(e.getId()),
        CategoryId.of(e.getCategoryId()),
        UserId.of(e.getOwnerId()),
        new Url(e.getUrl()),
        e.getTitle(),
        e.getDescription(),
        BookmarkState.valueOf(e.getState()),
        e.getPosition(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
