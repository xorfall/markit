package com.markit.bookmarking.presentation;

import com.markit.bookmarking.application.BookmarkService;
import com.markit.bookmarking.domain.Bookmark;
import com.markit.bookmarking.domain.BookmarkId;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.presentation.BookmarkDtos.AddBookmarkRequest;
import com.markit.bookmarking.presentation.BookmarkDtos.BookmarkResponse;
import com.markit.bookmarking.presentation.BookmarkDtos.ReorderRequest;
import com.markit.bookmarking.presentation.BookmarkDtos.UpdateBookmarkRequest;
import com.markit.identity.domain.UserId;
import com.markit.platform.security.AuthenticatedUser;
import com.markit.scraping.application.ScrapeOrchestrator;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bookmark endpoints (FR-BMK-004/005/006/007/008, api-contract §4). Scoped strictly to the user. */
@RestController
public class BookmarkController {

  private final BookmarkService bookmarks;
  private final ScrapeOrchestrator scraping;

  public BookmarkController(BookmarkService bookmarks, ScrapeOrchestrator scraping) {
    this.bookmarks = bookmarks;
    this.scraping = scraping;
  }

  @GetMapping("/api/v1/categories/{cid}/bookmarks")
  public List<BookmarkResponse> list(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID cid) {
    return bookmarks.list(principal.id(), CategoryId.of(cid)).stream()
        .map(BookmarkResponse::from)
        .toList();
  }

  @PostMapping("/api/v1/categories/{cid}/bookmarks")
  @ResponseStatus(HttpStatus.CREATED)
  public BookmarkResponse add(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID cid,
      @Valid @RequestBody AddBookmarkRequest request) {
    return BookmarkResponse.from(
        bookmarks.add(principal.id(), CategoryId.of(cid), request.url()));
  }

  @GetMapping("/api/v1/bookmarks/{id}")
  public BookmarkResponse get(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
    return BookmarkResponse.from(bookmarks.get(principal.id(), BookmarkId.of(id)));
  }

  @PatchMapping("/api/v1/bookmarks/{id}")
  public BookmarkResponse update(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateBookmarkRequest request) {
    UserId owner = principal.id();
    BookmarkId bookmarkId = BookmarkId.of(id);
    Bookmark result = null;
    if (request.title() != null || request.description() != null) {
      result = bookmarks.editDetails(owner, bookmarkId, request.title(), request.description());
    }
    if (request.categoryId() != null) {
      result = bookmarks.move(owner, bookmarkId, CategoryId.of(request.categoryId()));
    }
    if (result == null) {
      throw new IllegalArgumentException(
          "Provide a title, description and/or categoryId to update");
    }
    return BookmarkResponse.from(result);
  }

  @PutMapping("/api/v1/categories/{cid}/bookmarks/reorder")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reorder(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID cid,
      @Valid @RequestBody ReorderRequest request) {
    bookmarks.reorder(
        principal.id(),
        CategoryId.of(cid),
        request.orderedIds().stream().map(BookmarkId::of).toList());
  }

  @DeleteMapping("/api/v1/bookmarks/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
    bookmarks.delete(principal.id(), BookmarkId.of(id));
  }

  /** Manually re-trigger scraping (FR-SCR-004, api-contract §4): resets to PENDING, returns 202. */
  @PostMapping("/api/v1/bookmarks/{id}/rescrape")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void rescrape(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
    scraping.rescrape(principal.id(), BookmarkId.of(id));
  }
}
