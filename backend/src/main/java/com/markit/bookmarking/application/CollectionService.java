package com.markit.bookmarking.application;

import com.markit.bookmarking.application.BookmarkingExceptions.NotFoundException;
import com.markit.bookmarking.application.port.BookmarkRepository;
import com.markit.bookmarking.application.port.CategoryRepository;
import com.markit.bookmarking.application.port.CollectionRepository;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.Category;
import com.markit.bookmarking.domain.Collection;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.identity.domain.UserId;
import com.markit.shared.events.BookmarkDeletedPayload;
import com.markit.shared.events.EventTypes;
import com.markit.shared.outbox.OutboxWriter;
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
  private final CategoryRepository categories;
  private final BookmarkRepository bookmarks;
  private final OutboxWriter outbox;
  private final Clock clock;

  public CollectionService(
      CollectionRepository collections,
      CategoryRepository categories,
      BookmarkRepository bookmarks,
      OutboxWriter outbox,
      Clock clock) {
    this.collections = collections;
    this.categories = categories;
    this.bookmarks = bookmarks;
    this.outbox = outbox;
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

  /**
   * Delete a collection. The DB {@code ON DELETE CASCADE} removes every descendant category and
   * bookmark silently, which would leave the ES projection stale — so we emit a
   * {@code bookmark.deleted} event for every descendant bookmark (owner-scoped) in the same
   * transaction before deleting the collection.
   */
  @Transactional
  public void delete(UserId owner, CollectionId id) {
    Collection collection = require(owner, id);
    for (Category category : categories.findByCollectionAndOwner(id, owner)) {
      for (Bookmark bookmark : bookmarks.findByCategoryAndOwner(category.id(), owner)) {
        outbox.append(
            EventTypes.AGGREGATE_BOOKMARK,
            bookmark.id().value(),
            EventTypes.BOOKMARK_DELETED,
            new BookmarkDeletedPayload(bookmark.id().value()));
      }
    }
    collections.delete(collection);
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
