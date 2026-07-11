package com.markit.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.markit.identity.application.AuthExceptions.EmailAlreadyUsedException;
import com.markit.identity.application.port.PasswordHasher;
import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import com.markit.identity.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegisterUserServiceTest {

  private final UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
  private final PasswordHasher hasher = org.mockito.Mockito.mock(PasswordHasher.class);
  private final SessionIssuer sessionIssuer = org.mockito.Mockito.mock(SessionIssuer.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);

  private RegisterUserService service;

  @BeforeEach
  void setUp() {
    service = new RegisterUserService(users, hasher, sessionIssuer, clock);
  }

  @Test
  void should_HashPasswordAndSaveUser_When_EmailIsFree() {
    when(users.existsByEmail(any())).thenReturn(false);
    when(hasher.hash("password1")).thenReturn("HASH");
    var tokens = SessionTokens.bearer("a", "r", 900);
    when(sessionIssuer.issueFor(any())).thenReturn(tokens);

    var result = service.register("New@Example.com", "password1");

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().email()).isEqualTo(new Email("new@example.com"));
    assertThat(saved.getValue().passwordHash()).isEqualTo("HASH");
    assertThat(result).isEqualTo(tokens);
  }

  @Test
  void should_Reject_When_EmailAlreadyRegistered() {
    when(users.existsByEmail(any())).thenReturn(true);

    assertThatThrownBy(() -> service.register("dupe@example.com", "password1"))
        .isInstanceOf(EmailAlreadyUsedException.class);
    verify(users, never()).save(any());
  }

  @Test
  void should_Reject_When_PasswordTooShort() {
    assertThatThrownBy(() -> service.register("a@example.com", "short"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(users, never()).save(any(User.class));
  }
}
