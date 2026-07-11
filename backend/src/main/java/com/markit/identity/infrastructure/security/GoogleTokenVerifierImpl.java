package com.markit.identity.infrastructure.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.markit.identity.application.AuthExceptions.InvalidGoogleTokenException;
import com.markit.identity.application.port.GoogleTokenVerifier;
import java.util.Collections;
import org.springframework.stereotype.Component;

/** Verifies Google ID tokens against Google's public keys, checking audience + email verification. */
@Component
public class GoogleTokenVerifierImpl implements GoogleTokenVerifier {

  private final GoogleIdTokenVerifier verifier;

  public GoogleTokenVerifierImpl(SecurityProperties properties) {
    this.verifier =
        new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(properties.google().clientId()))
            .build();
  }

  @Override
  public GoogleIdentity verify(String idToken) {
    try {
      GoogleIdToken token = verifier.verify(idToken);
      if (token == null) {
        throw new InvalidGoogleTokenException();
      }
      GoogleIdToken.Payload payload = token.getPayload();
      if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
        throw new InvalidGoogleTokenException();
      }
      return new GoogleIdentity(payload.getSubject(), payload.getEmail());
    } catch (RuntimeException e) {
      throw e instanceof InvalidGoogleTokenException g ? g : new InvalidGoogleTokenException();
    } catch (Exception e) {
      throw new InvalidGoogleTokenException();
    }
  }
}
