package com.markit.bookmarking.application;

import com.markit.bookmarking.application.BookmarkingExceptions.NotFoundException;
import com.markit.bookmarking.application.port.CategoryRepository;
import com.markit.bookmarking.application.port.CollectionRepository;
import com.markit.bookmarking.domain.Category;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.identity.domain.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link Category}s (FR-BMK-002/003). Creating and moving verify the parent collection
 * belongs to the current user before touching anything; every load is owner-scoped (R-SEC-01).
 */
@Service
public class CategoryService {

  private final CategoryRepository categories;
  private final CollectionRepository collections;
  private final Clock clock;

  public CategoryService(
      CategoryRepository categories, CollectionRepository collections, Clock clock) {
    this.categories = categories;
    this.collections = collections;
    this.clock = clock;
  }

  @Transactional
  public Category create(UserId owner, CollectionId collectionId, String name) {
    requireCollection(owner, collectionId);
    int position = categories.findByCollectionAndOwner(collectionId, owner).size();
    Category category =
        Category.create(CategoryId.newId(), collectionId, owner, name, position, clock.instant());
    categories.save(category);
    return category;
  }

  @Transactional
  public Category rename(UserId owner, CategoryId id, String name) {
    Category category = require(owner, id);
    category.rename(name);
    categories.save(category);
    return category;
  }

  @Transactional
  public Category move(UserId owner, CategoryId id, CollectionId targetCollectionId) {
    Category category = require(owner, id);
    requireCollection(owner, targetCollectionId);
    int position = categories.findByCollectionAndOwner(targetCollectionId, owner).size();
    category.moveTo(targetCollectionId, position);
    categories.save(category);
    return category;
  }

  @Transactional
  public void delete(UserId owner, CategoryId id) {
    categories.delete(require(owner, id));
  }

  @Transactional
  public void reorder(UserId owner, CollectionId collectionId, List<CategoryId> orderedIds) {
    requireCollection(owner, collectionId);
    for (int position = 0; position < orderedIds.size(); position++) {
      Category category = require(owner, orderedIds.get(position));
      category.reposition(position);
      categories.save(category);
    }
  }

  @Transactional(readOnly = true)
  public List<Category> list(UserId owner, CollectionId collectionId) {
    requireCollection(owner, collectionId);
    return categories.findByCollectionAndOwner(collectionId, owner);
  }

  private Category require(UserId owner, CategoryId id) {
    return categories
        .findByIdAndOwner(id, owner)
        .orElseThrow(() -> new NotFoundException("Category not found"));
  }

  private void requireCollection(UserId owner, CollectionId collectionId) {
    collections
        .findByIdAndOwner(collectionId, owner)
        .orElseThrow(() -> new NotFoundException("Collection not found"));
  }
}
