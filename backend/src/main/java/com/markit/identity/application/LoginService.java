package com.markit.identity.application;

import com.markit.identity.application.AuthExceptions.InvalidCredentialsException;
import com.markit.identity.application.port.PasswordHasher;
import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import org.springframework.stereotype.Service;

/** Authenticates a user with email + password (FR-IDN-002). */
@Service
public class LoginService {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final SessionIssuer sessionIssuer;

  public LoginService(
      UserRepository users, PasswordHasher passwordHasher, SessionIssuer sessionIssuer) {
    this.users = users;
    this.passwordHasher = passwordHasher;
    this.sessionIssuer = sessionIssuer;
  }

  public SessionTokens login(String emailRaw, String rawPassword) {
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
    return sessionIssuer.issueFor(user.id());
  }
}
