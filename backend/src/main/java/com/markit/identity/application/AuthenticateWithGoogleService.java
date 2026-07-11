package com.markit.identity.application;

import com.markit.identity.application.port.GoogleTokenVerifier;
import com.markit.identity.application.port.GoogleTokenVerifier.GoogleIdentity;
import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import com.markit.identity.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signs a user in with Google (FR-IDN-003): matches by Google subject, else links to an existing
 * account by verified email, else creates a new account. Issues a session either way.
 */
@Service
public class AuthenticateWithGoogleService {

  private final GoogleTokenVerifier verifier;
  private final UserRepository users;
  private final SessionIssuer sessionIssuer;
  private final Clock clock;

  public AuthenticateWithGoogleService(
      GoogleTokenVerifier verifier,
      UserRepository users,
      SessionIssuer sessionIssuer,
      Clock clock) {
    this.verifier = verifier;
    this.users = users;
    this.sessionIssuer = sessionIssuer;
    this.clock = clock;
  }

  @Transactional
  public SessionTokens authenticate(String idToken) {
    GoogleIdentity identity = verifier.verify(idToken); // throws if invalid/unverified
    Email email = new Email(identity.email());

    Optional<User> bySubject = users.findByGoogleSub(identity.subject());
    if (bySubject.isPresent()) {
      return sessionIssuer.issueFor(bySubject.get().id());
    }

    Optional<User> byEmail = users.findByEmail(email);
    if (byEmail.isPresent()) {
      User existing = byEmail.get();
      existing.linkGoogle(identity.subject()); // link Google to the existing account
      users.save(existing);
      return sessionIssuer.issueFor(existing.id());
    }

    User created =
        User.registerWithGoogle(UserId.newId(), email, identity.subject(), clock.instant());
    users.save(created);
    return sessionIssuer.issueFor(created.id());
  }
}
