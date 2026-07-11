package com.markit.identity.application;

import com.markit.identity.application.AuthExceptions.InvalidRefreshTokenException;
import com.markit.identity.application.port.RefreshTokenStore;
import com.markit.identity.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rotates a refresh token: validates the old, revokes it, issues a new pair (FR-IDN-004). */
@Service
public class RefreshSessionService {

  private final RefreshTokenStore refreshTokens;
  private final SessionIssuer sessionIssuer;

  public RefreshSessionService(RefreshTokenStore refreshTokens, SessionIssuer sessionIssuer) {
    this.refreshTokens = refreshTokens;
    this.sessionIssuer = sessionIssuer;
  }

  @Transactional
  public SessionTokens refresh(String rawRefreshToken) {
    UserId userId =
        refreshTokens.validate(rawRefreshToken).orElseThrow(InvalidRefreshTokenException::new);
    refreshTokens.revoke(rawRefreshToken); // rotation: the old token is single-use
    return sessionIssuer.issueFor(userId);
  }
}
