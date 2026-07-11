package com.markit.bookmarking.application;

import com.markit.bookmarking.application.BookmarkingExceptions.NotFoundException;
import com.markit.bookmarking.application.port.CollectionRepository;
import com.markit.bookmarking.domain.Collection;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.identity.domain.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link Collection}s (FR-BMK-001/003). Every operation is scoped to the current user:
 * the target is loaded via {@code findByIdAndOwner} and a miss is a 404 — a collection owned by
 * another user is indistinguishable from an absent one (R-SEC-01).
 */
@Service
public class CollectionService {

  private final CollectionRepository collections;
  private final Clock clock;

  public CollectionService(CollectionRepository collections, Clock clock) {
    this.collections = collections;
    this.clock = clock;
  }

  @Transactional
  public Collection create(UserId owner, String name) {
    int position = collections.findByOwner(owner).size();
    Collection collection =
        Collection.create(CollectionId.newId(), owner, name, position, clock.instant());
    collections.save(collection);
    return collection;
  }

  @Transactional
  public Collection rename(UserId owner, CollectionId id, String name) {
    Collection collection = require(owner, id);
    collection.rename(name);
    collections.save(collection);
    return collection;
  }

  @Transactional
  public void delete(UserId owner, CollectionId id) {
    collections.delete(require(owner, id));
  }

  @Transactional
  public void reorder(UserId owner, List<CollectionId> orderedIds) {
    for (int position = 0; position < orderedIds.size(); position++) {
      Collection collection = require(owner, orderedIds.get(position));
      collection.reposition(position);
      collections.save(collection);
    }
  }

  @Transactional(readOnly = true)
  public List<Collection> list(UserId owner) {
    return collections.findByOwner(owner);
  }

  private Collection require(UserId owner, CollectionId id) {
    return collections
        .findByIdAndOwner(id, owner)
        .orElseThrow(() -> new NotFoundException("Collection not found"));
  }
}
