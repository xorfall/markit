package com.markit.bookmarking.application;

import com.markit.bookmarking.application.BookmarkingExceptions.DuplicateUrlException;
import com.markit.bookmarking.application.BookmarkingExceptions.NotFoundException;
import com.markit.bookmarking.application.port.BookmarkRepository;
import com.markit.bookmarking.application.port.CategoryRepository;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.BookmarkId;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.domain.Url;
import com.markit.identity.domain.UserId;
import com.markit.shared.events.BookmarkDeletedPayload;
import com.markit.shared.events.BookmarkUpsertedPayload;
import com.markit.shared.events.EventTypes;
import com.markit.shared.events.ScrapeRequestedPayload;
import com.markit.shared.outbox.OutboxWriter;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link Bookmark}s (FR-BMK-004/005/006/007/008). Adding and moving verify the target
 * category belongs to the current user and re-check the duplicate URL in the destination. Duplicate
 * prevention is race-safe: a pre-check plus a catch of the DB {@code UNIQUE} violation (data-model
 * §3). Every load is owner-scoped (R-SEC-01).
 */
@Service
public class BookmarkService {

  private final BookmarkRepository bookmarks;
  private final CategoryRepository categories;
  private final OutboxWriter outbox;
  private final Clock clock;

  public BookmarkService(
      BookmarkRepository bookmarks,
      CategoryRepository categories,
      OutboxWriter outbox,
      Clock clock) {
    this.bookmarks = bookmarks;
    this.categories = categories;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public Bookmark add(UserId owner, CategoryId categoryId, String rawUrl) {
    requireCategory(owner, categoryId);
    Url url = new Url(rawUrl);
    requireNoDuplicate(categoryId, url);
    int position = bookmarks.findByCategoryAndOwner(categoryId, owner).size();
    Bookmark bookmark =
        Bookmark.add(BookmarkId.newId(), categoryId, owner, url, position, clock.instant());
    persist(bookmark);
    appendUpserted(bookmark);
    appendScrapeRequested(bookmark);
    return bookmark;
  }

  @Transactional
  public Bookmark editDetails(UserId owner, BookmarkId id, String title, String description) {
    Bookmark bookmark = require(owner, id);
    bookmark.editDetails(title, description, clock.instant());
    bookmarks.save(bookmark);
    appendUpserted(bookmark);
    return bookmark;
  }

  @Transactional
  public Bookmark move(UserId owner, BookmarkId id, CategoryId targetCategoryId) {
    Bookmark bookmark = require(owner, id);
    requireCategory(owner, targetCategoryId);
    requireNoDuplicate(targetCategoryId, bookmark.url());
    int position = bookmarks.findByCategoryAndOwner(targetCategoryId, owner).size();
    bookmark.moveTo(targetCategoryId, position, clock.instant());
    persist(bookmark);
    appendUpserted(bookmark);
    return bookmark;
  }

  @Transactional
  public void delete(UserId owner, BookmarkId id) {
    Bookmark bookmark = require(owner, id);
    bookmarks.delete(bookmark);
    appendDeleted(bookmark.id());
  }

  @Transactional
  public void reorder(UserId owner, CategoryId categoryId, List<BookmarkId> orderedIds) {
    requireCategory(owner, categoryId);
    for (int position = 0; position < orderedIds.size(); position++) {
      Bookmark bookmark = require(owner, orderedIds.get(position));
      bookmark.reposition(position, clock.instant());
      bookmarks.save(bookmark);
    }
  }

  @Transactional(readOnly = true)
  public Bookmark get(UserId owner, BookmarkId id) {
    return require(owner, id);
  }

  @Transactional(readOnly = true)
  public List<Bookmark> list(UserId owner, CategoryId categoryId) {
    requireCategory(owner, categoryId);
    return bookmarks.findByCategoryAndOwner(categoryId, owner);
  }

  private Bookmark persist(Bookmark bookmark) {
    try {
      bookmarks.save(bookmark);
      return bookmark;
    } catch (DataIntegrityViolationException e) {
      // Race: another concurrent add slipped the same URL past the pre-check (data-model §3).
      throw new DuplicateUrlException();
    }
  }

  /**
   * Append a {@code bookmark.upserted} event within the current transaction, so the state row and
   * the outbox row commit atomically (no dual write, ADR-0003).
   */
  private void appendUpserted(Bookmark bookmark) {
    outbox.append(
        EventTypes.AGGREGATE_BOOKMARK,
        bookmark.id().value(),
        EventTypes.BOOKMARK_UPSERTED,
        new BookmarkUpsertedPayload(
            bookmark.id().value(),
            bookmark.ownerId().value(),
            bookmark.categoryId().value(),
            bookmark.url().value(),
            bookmark.title(),
            bookmark.description(),
            bookmark.state().name(),
            bookmark.createdAt()));
  }

  /**
   * Dispatch a {@code scrape.requested} event within the current transaction (same-tx as the state
   * change, ADR-0003), so the scrape kicks off reliably via the relay without a direct broker call.
   */
  private void appendScrapeRequested(Bookmark bookmark) {
    outbox.append(
        EventTypes.AGGREGATE_BOOKMARK,
        bookmark.id().value(),
        EventTypes.SCRAPE_REQUESTED,
        new ScrapeRequestedPayload(
            bookmark.id().value(), bookmark.ownerId().value(), bookmark.url().value()));
  }

  private void appendDeleted(BookmarkId id) {
    outbox.append(
        EventTypes.AGGREGATE_BOOKMARK,
        id.value(),
        EventTypes.BOOKMARK_DELETED,
        new BookmarkDeletedPayload(id.value()));
  }

  private void requireNoDuplicate(CategoryId categoryId, Url url) {
    if (bookmarks.existsByCategoryAndUrl(categoryId, url.value())) {
      throw new DuplicateUrlException();
    }
  }

  private Bookmark require(UserId owner, BookmarkId id) {
    return bookmarks
        .findByIdAndOwner(id, owner)
        .orElseThrow(() -> new NotFoundException("Bookmark not found"));
  }

  private void requireCategory(UserId owner, CategoryId categoryId) {
    categories
        .findByIdAndOwner(categoryId, owner)
        .orElseThrow(() -> new NotFoundException("Category not found"));
  }
}
