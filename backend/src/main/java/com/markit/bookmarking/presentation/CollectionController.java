package com.markit.bookmarking.presentation;

import com.markit.bookmarking.application.CategoryService;
import com.markit.bookmarking.application.CollectionService;
import com.markit.bookmarking.domain.CollectionId;
import com.markit.bookmarking.presentation.CategoryDtos.CategoryResponse;
import com.markit.bookmarking.presentation.CollectionDtos.CollectionResponse;
import com.markit.bookmarking.presentation.CollectionDtos.CreateCollectionRequest;
import com.markit.bookmarking.presentation.CollectionDtos.RenameCollectionRequest;
import com.markit.bookmarking.presentation.CollectionDtos.ReorderRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Collection endpoints (FR-BMK-001/003, api-contract §3). Scoped strictly to the token's user. */
@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

  private final CollectionService collections;
  private final CategoryService categories;

  public CollectionController(CollectionService collections, CategoryService categories) {
    this.collections = collections;
    this.categories = categories;
  }

  @GetMapping
  public List<CollectionResponse> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String expand) {
    boolean withCategories = expand != null && expand.contains("categories");
    return collections.list(principal.id()).stream()
        .map(
            collection ->
                withCategories
                    ? CollectionResponse.expanded(collection, categoriesOf(principal, collection.id()))
                    : CollectionResponse.from(collection))
        .toList();
  }

  private List<CategoryResponse> categoriesOf(AuthenticatedUser principal, CollectionId collectionId) {
    return categories.list(principal.id(), collectionId).stream()
        .map(CategoryResponse::from)
        .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CollectionResponse create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateCollectionRequest request) {
    return CollectionResponse.from(collections.create(principal.id(), request.name()));
  }

  @PatchMapping("/{id}")
  public CollectionResponse rename(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable UUID id,
      @Valid @RequestBody RenameCollectionRequest request) {
    return CollectionResponse.from(
        collections.rename(principal.id(), CollectionId.of(id), request.name()));
  }

  @PutMapping("/reorder")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reorder(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody ReorderRequest request) {
    collections.reorder(principal.id(), request.orderedIds().stream().map(CollectionId::of).toList());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
    collections.delete(principal.id(), CollectionId.of(id));
  }
}
