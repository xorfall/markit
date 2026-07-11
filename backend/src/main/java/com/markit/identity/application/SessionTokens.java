package com.markit.identity.application;

/** The pair of tokens issued on register/login/refresh (ADR-0006). */
public record SessionTokens(
    String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {

  public static SessionTokens bearer(String accessToken, String refreshToken, long expiresIn) {
    return new SessionTokens(accessToken, refreshToken, "Bearer", expiresIn);
  }
}
