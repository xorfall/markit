package com.markit.identity.application;

import com.markit.identity.application.port.RefreshTokenStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Logs out by revoking the presented refresh token (FR-IDN-004). Idempotent. */
@Service
public class LogoutService {

  private final RefreshTokenStore refreshTokens;

  public LogoutService(RefreshTokenStore refreshTokens) {
    this.refreshTokens = refreshTokens;
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokens.revoke(rawRefreshToken);
  }
}
