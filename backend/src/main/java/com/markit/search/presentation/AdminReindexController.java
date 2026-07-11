package com.markit.search.presentation;

import com.markit.search.infrastructure.ReindexService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated admin endpoint to rebuild the ES projection from Postgres (NFR-CONS-004). Secured by
 * the default rule (any authenticated request); a finer role check can be layered later.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminReindexController {

  private final ReindexService reindexService;

  public AdminReindexController(ReindexService reindexService) {
    this.reindexService = reindexService;
  }

  @PostMapping("/reindex")
  public ResponseEntity<Map<String, Long>> reindex() {
    long indexed = reindexService.reindexAll();
    return ResponseEntity.ok(Map.of("indexed", indexed));
  }
}
