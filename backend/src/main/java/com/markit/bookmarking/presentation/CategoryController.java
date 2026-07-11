package com.markit.bookmarking.presentation;

import com.markit.bookmarking.application.CategoryService;
import com.markit.bookmarking.domain.CategoryId;
import com.markit.bookmarking.domain.Category;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.bookmarking.presentation.CategoryDtos.CategoryResponse;
import com.markit.bookmarking.presentation.CategoryDtos.CreateCategoryRequest;
import com.markit.bookmarking.presentation.CategoryDtos.ReorderRequest;
import com.markit.bookmarking.presentation.CategoryDtos.UpdateCategoryRequest;
import com.markit.identity.domain.UserId;
import com.markit.platform.security.AuthenticatedUser;
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

/** Category endpoints (FR-BMK-002/003, api-contract §3). Scoped strictly to the token's user. */
@RestController
public class CategoryController {

  private final CategoryService categories;

  public CategoryController(CategoryService categories) {
    this.categories = categories;
  }

  @GetMapping("/api/v1/collections/{cid}/categories")
  public List<CategoryResponse> list(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID cid) {
    return categories.list(principal.id(), CollectionId.of(cid)).stream()
        .map(CategoryResponse::from)
        .toList();
  }

  @PostMapping("/api/v1/collections/{cid}/categories")
  @ResponseStatus(HttpStatus.CREATED)
  public CategoryResponse create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID cid,
      @Valid @RequestBody CreateCategoryRequest request) {
    return CategoryResponse.from(
        categories.create(principal.id(), CollectionId.of(cid), request.name()));
  }

  @PatchMapping("/api/v1/categories/{id}")
  public CategoryResponse update(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCategoryRequest request) {
    UserId owner = principal.id();
    CategoryId categoryId = CategoryId.of(id);
    Category result = null;
    if (request.collectionId() != null) {
      result = categories.move(owner, categoryId, CollectionId.of(request.collectionId()));
    }
    if (request.name() != null && !request.name().isBlank()) {
      result = categories.rename(owner, categoryId, request.name());
    }
    if (result == null) {
      throw new IllegalArgumentException("Provide a name and/or a collectionId to update");
    }
    return CategoryResponse.from(result);
  }

  @PutMapping("/api/v1/collections/{cid}/categories/reorder")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reorder(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID cid,
      @Valid @RequestBody ReorderRequest request) {
    categories.reorder(
        principal.id(),
        CollectionId.of(cid),
        request.orderedIds().stream().map(CategoryId::of).toList());
  }

  @DeleteMapping("/api/v1/categories/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
    categories.delete(principal.id(), CategoryId.of(id));
  }
}
