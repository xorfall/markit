package com.markit.search.presentation;

import com.markit.platform.security.AuthenticatedUser;
import com.markit.search.infrastructure.SearchService;
import com.markit.search.presentation.SearchDtos.SearchResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content-aware search endpoint (FR-SRC-001/002/003, api-contract §5). Ownership is derived from the
 * authenticated principal, never from client-supplied ids — results are always scoped to the caller
 * (NFR-SEC-002).
 *
 * <p>{@code collectionId} is accepted for forward compatibility but not yet honored: the ES
 * projection carries {@code categoryId}, not {@code collectionId}, so scoping is by category only.
 */
@RestController
public class SearchController {

  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/api/v1/search")
  public SearchResponse search(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "collectionId", required = false) UUID collectionId,
      @RequestParam(name = "categoryId", required = false) UUID categoryId,
      @RequestParam(name = "limit", defaultValue = "20") int limit,
      @RequestParam(name = "cursor", required = false) String cursor) {
    return SearchResponse.from(
        searchService.search(principal.id(), q, categoryId, limit, cursor));
  }
}
