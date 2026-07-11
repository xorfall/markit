package com.markit.identity.application.port;

import com.markit.identity.domain.UserId;
import java.util.Optional;

/** Issues and verifies short-lived stateless access tokens (JWT — ADR-0006). */
public interface AccessTokenService {

  String issue(UserId userId);

  /** Returns the subject if the token is valid and unexpired, else empty. */
  Optional<UserId> verify(String token);

  long validitySeconds();
}
