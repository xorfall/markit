package com.markit.identity.application;

import com.markit.identity.application.port.AccessTokenService;
import com.markit.identity.application.port.RefreshTokenStore;
import com.markit.identity.domain.UserId;
import org.springframework.stereotype.Component;

/** Issues a fresh access + refresh token pair for a user (shared by register/login/refresh). */
@Component
public class SessionIssuer {

  private final AccessTokenService accessTokens;
  private final RefreshTokenStore refreshTokens;

  public SessionIssuer(AccessTokenService accessTokens, RefreshTokenStore refreshTokens) {
    this.accessTokens = accessTokens;
    this.refreshTokens = refreshTokens;
  }

  public SessionTokens issueFor(UserId userId) {
    String access = accessTokens.issue(userId);
    String refresh = refreshTokens.issue(userId);
    return SessionTokens.bearer(access, refresh, accessTokens.validitySeconds());
  }
}
