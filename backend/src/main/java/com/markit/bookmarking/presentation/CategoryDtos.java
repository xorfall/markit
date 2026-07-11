package com.markit.bookmarking.presentation;

import com.markit.bookmarking.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request/response payloads for the Categories API (api-contract §3). */
public final class CategoryDtos {

  private CategoryDtos() {}

  public record CreateCategoryRequest(@NotBlank @Size(max = 200) String name) {}

  /** Rename and/or move: both fields optional (api-contract §3, {@code {name?, collectionId?}}). */
  public record UpdateCategoryRequest(@Size(max = 200) String name, UUID collectionId) {}

  public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {}

  public record CategoryResponse(
      String id, String collectionId, String name, int position, String createdAt) {

    public static CategoryResponse from(Category category) {
      return new CategoryResponse(
          category.id().asString(),
          category.collectionId().asString(),
          category.name(),
          category.position(),
          category.createdAt().toString());
    }
  }
}
