package com.markit.bookmarking.infrastructure.persistence;

import com.markit.bookmarking.application.port.CategoryRepository;
import com.markit.bookmarking.domain.Category;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.identity.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the domain {@link CategoryRepository} port. */
@Repository
public class CategoryRepositoryAdapter implements CategoryRepository {

  private final SpringDataCategoryRepository jpa;

  CategoryRepositoryAdapter(SpringDataCategoryRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Category> findByIdAndOwner(CategoryId id, UserId owner) {
    return jpa.findByIdAndOwnerId(id.value(), owner.value())
        .map(CategoryRepositoryAdapter::toDomain);
  }

  @Override
  public List<Category> findByCollectionAndOwner(CollectionId collectionId, UserId owner) {
    return jpa
        .findByCollectionIdAndOwnerIdOrderByPositionAsc(collectionId.value(), owner.value())
        .stream()
        .map(CategoryRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public void save(Category category) {
    jpa.save(
        new CategoryJpaEntity(
            category.id().value(),
            category.collectionId().value(),
            category.ownerId().value(),
            category.name(),
            category.position(),
            category.createdAt()));
  }

  @Override
  public void delete(Category category) {
    jpa.deleteById(category.id().value());
  }

  private static Category toDomain(CategoryJpaEntity e) {
    return Category.rehydrate(
        CategoryId.of(e.getId()),
        CollectionId.of(e.getCollectionId()),
        UserId.of(e.getOwnerId()),
        e.getName(),
        e.getPosition(),
        e.getCreatedAt());
  }
}
