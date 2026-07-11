package com.markit.bookmarking.presentation;

import com.markit.bookmarking.domain.Collection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request/response payloads for the Collections API (api-contract §3). */
public final class CollectionDtos {

  private CollectionDtos() {}

  public record CreateCollectionRequest(@NotBlank @Size(max = 200) String name) {}

  public record RenameCollectionRequest(@NotBlank @Size(max = 200) String name) {}

  public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {}

  public record CollectionResponse(
      String id,
      String name,
      int position,
      String createdAt,
      List<CategoryDtos.CategoryResponse> categories) {

    /** Without nested categories (the categories field is null). */
    public static CollectionResponse from(Collection collection) {
      return new CollectionResponse(
          collection.id().asString(),
          collection.name(),
          collection.position(),
          collection.createdAt().toString(),
          null);
    }

    /** With nested categories (used for {@code ?expand=categories}). */
    public static CollectionResponse expanded(
        Collection collection, List<CategoryDtos.CategoryResponse> categories) {
      return new CollectionResponse(
          collection.id().asString(),
          collection.name(),
          collection.position(),
          collection.createdAt().toString(),
          categories);
    }
  }
}
