package com.markit.bookmarking.infrastructure.persistence;

import com.markit.bookmarking.application.port.CollectionRepository;
import com.markit.bookmarking.domain.Collection;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.identity.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the domain {@link CollectionRepository} port. */
@Repository
public class CollectionRepositoryAdapter implements CollectionRepository {

  private final SpringDataCollectionRepository jpa;

  CollectionRepositoryAdapter(SpringDataCollectionRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Collection> findByIdAndOwner(CollectionId id, UserId owner) {
    return jpa.findByIdAndOwnerId(id.value(), owner.value())
        .map(CollectionRepositoryAdapter::toDomain);
  }

  @Override
  public List<Collection> findByOwner(UserId owner) {
    return jpa.findByOwnerIdOrderByPositionAsc(owner.value()).stream()
        .map(CollectionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public void save(Collection collection) {
    jpa.save(
        new CollectionJpaEntity(
            collection.id().value(),
            collection.ownerId().value(),
            collection.name(),
            collection.position(),
            collection.createdAt()));
  }

  @Override
  public void delete(Collection collection) {
    jpa.deleteById(collection.id().value());
  }

  private static Collection toDomain(CollectionJpaEntity e) {
    return Collection.rehydrate(
        CollectionId.of(e.getId()),
        UserId.of(e.getOwnerId()),
        e.getName(),
        e.getPosition(),
        e.getCreatedAt());
  }
}
