package com.markit.platform.sse;

import com.markit.platform.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events stream of the authenticated user's bookmark lifecycle (FR-SCR-003). The stream
 * is scoped strictly to {@code principal.id()} (NFR-SEC-002). Because {@code EventSource} cannot send
 * an Authorization header, the JWT may be passed as the {@code access_token} query parameter, which
 * the security filter also accepts.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventsController {

  private final SseEmitterRegistry registry;

  public EventsController(SseEmitterRegistry registry) {
    this.registry = registry;
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser principal) {
    return registry.create(principal.id());
  }
}
