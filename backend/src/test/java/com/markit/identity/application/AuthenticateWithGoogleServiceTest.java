package com.markit.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.markit.identity.application.AuthExceptions.InvalidGoogleTokenException;
import com.markit.identity.application.port.GoogleTokenVerifier;
import com.markit.identity.application.port.GoogleTokenVerifier.GoogleIdentity;
import com.markit.identity.application.port.UserRepository;
import com.markit.identity.domain.Email;
import com.markit.identity.domain.User;
import com.markit.identity.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthenticateWithGoogleServiceTest {

  private final GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
  private final UserRepository users = mock(UserRepository.class);
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
  private final AuthenticateWithGoogleService service =
      new AuthenticateWithGoogleService(verifier, users, sessionIssuer, clock);

  private static final SessionTokens TOKENS = SessionTokens.bearer("a", "r", 900);

  @Test
  void should_CreateAccount_When_GoogleUserUnknown() {
    when(verifier.verify("tok")).thenReturn(new GoogleIdentity("sub1", "new@example.com"));
    when(users.findByGoogleSub("sub1")).thenReturn(Optional.empty());
    when(users.findByEmail(any())).thenReturn(Optional.empty());
    when(sessionIssuer.issueFor(any())).thenReturn(TOKENS);

    assertThat(service.authenticate("tok")).isEqualTo(TOKENS);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().googleSub()).isEqualTo("sub1");
    assertThat(saved.getValue().hasPassword()).isFalse();
  }

  @Test
  void should_Login_When_GoogleSubjectAlreadyKnown() {
    var existing =
        User.registerWithGoogle(
            UserId.newId(), new Email("known@example.com"), "sub1", clock.instant());
    when(verifier.verify("tok")).thenReturn(new GoogleIdentity("sub1", "known@example.com"));
    when(users.findByGoogleSub("sub1")).thenReturn(Optional.of(existing));
    when(sessionIssuer.issueFor(existing.id())).thenReturn(TOKENS);

    assertThat(service.authenticate("tok")).isEqualTo(TOKENS);
    verify(users, never()).save(any());
  }

  @Test
  void should_LinkGoogle_When_EmailMatchesPasswordAccount() {
    var passwordUser =
        User.registerWithPassword(
            UserId.newId(), new Email("me@example.com"), "HASH", clock.instant());
    when(verifier.verify("tok")).thenReturn(new GoogleIdentity("sub9", "me@example.com"));
    when(users.findByGoogleSub("sub9")).thenReturn(Optional.empty());
    when(users.findByEmail(new Email("me@example.com"))).thenReturn(Optional.of(passwordUser));
    when(sessionIssuer.issueFor(passwordUser.id())).thenReturn(TOKENS);

    assertThat(service.authenticate("tok")).isEqualTo(TOKENS);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().googleSub()).isEqualTo("sub9"); // linked
  }

  @Test
  void should_Propagate_When_TokenInvalid() {
    when(verifier.verify("bad")).thenThrow(new InvalidGoogleTokenException());
    assertThatThrownBy(() -> service.authenticate("bad"))
        .isInstanceOf(InvalidGoogleTokenException.class);
    verify(users, never()).save(any());
  }
}
