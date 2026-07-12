package com.markit.identity.application;

import com.markit.identity.application.AuthExceptions.InvalidCredentialsException;
import com.markit.identity.application.port.PasswordHasher;
import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

/** Authenticates a user with email + password (FR-IDN-002). */
@Service
public class LoginService {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final SessionIssuer sessionIssuer;
  private final Counter loginSuccessCounter;
  private final Counter loginFailureCounter;

  public LoginService(
      UserRepository users,
      PasswordHasher passwordHasher,
      SessionIssuer sessionIssuer,
      MeterRegistry meterRegistry) {
    this.users = users;
    this.passwordHasher = passwordHasher;
    this.sessionIssuer = sessionIssuer;
    // C2 domain metric: a login-failure spike is a credential-stuffing signal (R-SEC-01/03).
    this.loginSuccessCounter = meterRegistry.counter("markit.auth.logins", "result", "success");
    this.loginFailureCounter = meterRegistry.counter("markit.auth.logins", "result", "failure");
  }

  public SessionTokens login(String emailRaw, String rawPassword) {
    try {
      Email email = new Email(emailRaw);
      // Same exception for unknown user and wrong password — non-enumerating (NFR-SEC-002 spirit).
      User user =
          users
              .findByEmail(email)
              .filter(User::hasPassword)
              .orElseThrow(InvalidCredentialsException::new);
      if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
        throw new InvalidCredentialsException();
      }
      SessionTokens tokens = sessionIssuer.issueFor(user.id());
      loginSuccessCounter.increment();
      return tokens;
    } catch (InvalidCredentialsException ex) {
      loginFailureCounter.increment();
      throw ex;
    }
  }
}
