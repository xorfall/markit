package com.markit.bookmarking.application.port;

import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.BookmarkId;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.identity.domain.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the {@link Bookmark} aggregate. Every read is scoped by owner (R-SEC-01).
 */
public interface BookmarkRepository {

  Optional<Bookmark> findByIdAndOwner(BookmarkId id, UserId owner);

  List<Bookmark> findByCategoryAndOwner(CategoryId categoryId, UserId owner);

  boolean existsByCategoryAndUrl(CategoryId categoryId, String url);

  void save(Bookmark bookmark);

  void delete(Bookmark bookmark);
}
