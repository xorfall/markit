package com.markit.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.markit.identity.application.AuthExceptions.InvalidCredentialsException;
import com.markit.identity.application.port.PasswordHasher;
import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import com.markit.identity.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoginServiceTest {

  private final UserRepository users = mock(UserRepository.class);
  private final PasswordHasher hasher = mock(PasswordHasher.class);
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final LoginService service = new LoginService(users, hasher, sessionIssuer);

  private static User userWithPassword() {
    return User.registerWithPassword(
        UserId.newId(), new Email("a@example.com"), "HASH", Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void should_IssueTokens_When_CredentialsValid() {
    var user = userWithPassword();
    when(users.findByEmail(new Email("a@example.com"))).thenReturn(Optional.of(user));
    when(hasher.matches("password1", "HASH")).thenReturn(true);
    var tokens = SessionTokens.bearer("a", "r", 900);
    when(sessionIssuer.issueFor(user.id())).thenReturn(tokens);

    assertThat(service.login("a@example.com", "password1")).isEqualTo(tokens);
  }

  @Test
  void should_Reject_When_UserUnknown() {
    when(users.findByEmail(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.login("nope@example.com", "password1"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void should_Reject_When_PasswordWrong() {
    when(users.findByEmail(any())).thenReturn(Optional.of(userWithPassword()));
    when(hasher.matches(eq("bad"), any())).thenReturn(false);
    assertThatThrownBy(() -> service.login("a@example.com", "bad"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void should_Reject_When_AccountHasNoPassword() {
    var oauthOnly =
        User.rehydrate(
            UserId.newId(), new Email("a@example.com"), null, "google-sub", Instant.now());
    when(users.findByEmail(any())).thenReturn(Optional.of(oauthOnly));
    assertThatThrownBy(() -> service.login("a@example.com", "password1"))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
