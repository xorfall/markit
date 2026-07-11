package com.markit.bookmarking.presentation;

import com.markit.bookmarking.domain.Bookmark;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request/response payloads for the Bookmarks API (api-contract §4). */
public final class BookmarkDtos {

  private BookmarkDtos() {}

  public record AddBookmarkRequest(@NotBlank @Size(max = 2048) String url) {}

  /** Edit and/or move: all fields optional (api-contract §4, {@code {title?, description?, categoryId?}}). */
  public record UpdateBookmarkRequest(
      @Size(max = 2048) String title, String description, UUID categoryId) {}

  public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {}

  public record BookmarkResponse(
      String id,
      String categoryId,
      String url,
      String title,
      String description,
      String state,
      int position,
      String createdAt,
      String updatedAt) {

    public static BookmarkResponse from(Bookmark bookmark) {
      return new BookmarkResponse(
          bookmark.id().asString(),
          bookmark.categoryId().asString(),
          bookmark.url().value(),
          bookmark.title(),
          bookmark.description(),
          bookmark.state().name(),
          bookmark.position(),
          bookmark.createdAt().toString(),
          bookmark.updatedAt().toString());
    }
  }
}
