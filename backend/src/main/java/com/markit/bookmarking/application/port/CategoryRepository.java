package com.markit.bookmarking.application.port;

import com.markit.bookmarking.domain.Category;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.identity.domain.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the {@link Category} aggregate. Every read is scoped by owner (R-SEC-01).
 */
public interface CategoryRepository {

  Optional<Category> findByIdAndOwner(CategoryId id, UserId owner);

  List<Category> findByCollectionAndOwner(CollectionId collectionId, UserId owner);

  void save(Category category);

  void delete(Category category);
}
