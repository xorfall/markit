package com.markit.identity.application.port;

/** Verifies a Google-issued ID token and returns the verified identity (FR-IDN-003, ADR-0006). */
public interface GoogleTokenVerifier {

  /**
   * @throws com.markit.identity.application.AuthExceptions.InvalidGoogleTokenException if the token
   *     is invalid, has the wrong audience, or its email is unverified.
   */
  GoogleIdentity verify(String idToken);

  /** A verified Google identity: stable subject id + verified email. */
  record GoogleIdentity(String subject, String email) {}
}
