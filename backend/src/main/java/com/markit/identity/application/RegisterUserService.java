package com.markit.identity.application;

import com.markit.identity.application.AuthExceptions.EmailAlreadyUsedException;
import com.markit.identity.application.port.PasswordHasher;
import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import com.markit.identity.domain.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers a new user with email + password (FR-IDN-001). */
@Service
public class RegisterUserService {

  static final int MIN_PASSWORD_LENGTH = 8;

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final SessionIssuer sessionIssuer;
  private final Clock clock;

  public RegisterUserService(
      UserRepository users,
      PasswordHasher passwordHasher,
      SessionIssuer sessionIssuer,
      Clock clock) {
    this.users = users;
    this.passwordHasher = passwordHasher;
    this.sessionIssuer = sessionIssuer;
    this.clock = clock;
  }

  @Transactional
  public SessionTokens register(String emailRaw, String rawPassword) {
    Email email = new Email(emailRaw); // validates + normalizes
    requireValidPassword(rawPassword);
    if (users.existsByEmail(email)) {
      throw new EmailAlreadyUsedException();
    }
    User user =
        User.registerWithPassword(
            UserId.newId(), email, passwordHasher.hash(rawPassword), clock.instant());
    users.save(user);
    return sessionIssuer.issueFor(user.id());
  }

  private static void requireValidPassword(String rawPassword) {
    if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
    }
  }
}
