package com.markit.platform.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trivial liveness/version endpoint for the walking skeleton (S0). Real health is served by
 * Spring Boot Actuator at {@code /actuator/health} with liveness/readiness probes.
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

  /** Minimal service identity response. */
  public record PingResponse(String status, String service) {}

  @GetMapping("/ping")
  public PingResponse ping() {
    return new PingResponse("ok", "markit-backend");
  }
}
