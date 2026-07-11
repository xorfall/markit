package com.markit.identity.presentation;

import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.User;
import com.markit.identity.presentation.AuthDtos.UserResponse;
import com.markit.platform.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns the authenticated user's profile (FR-IDN-005 — scoped strictly to the token's user). */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

  private final UserRepository users;

  public MeController(UserRepository users) {
    this.users = users;
  }

  @GetMapping
  public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
    return users
        .findById(principal.id())
        .map(MeController::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private static UserResponse toResponse(User user) {
    return new UserResponse(
        user.id().asString(), user.email().value(), user.createdAt().toString());
  }
}
