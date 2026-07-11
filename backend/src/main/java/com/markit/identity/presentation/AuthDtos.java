package com.markit.identity.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response payloads for the Identity API (api-contract §2). */
public final class AuthDtos {

  private AuthDtos() {}

  public record RegisterRequest(
      @Email @NotBlank String email, @NotBlank @Size(min = 8, max = 200) String password) {}

  public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record LogoutRequest(@NotBlank String refreshToken) {}

  public record TokenResponse(
      String accessToken, String refreshToken, String tokenType, long expiresIn) {}

  public record UserResponse(String id, String email, String createdAt) {}
}
