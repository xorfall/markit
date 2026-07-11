package com.markit.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.markit.identity.application.AuthExceptions.InvalidRefreshTokenException;
import com.markit.identity.application.port.RefreshTokenStore;
import com.markit.identity.domain.UserId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RefreshSessionServiceTest {

  private final RefreshTokenStore store = mock(RefreshTokenStore.class);
  private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
  private final RefreshSessionService service = new RefreshSessionService(store, sessionIssuer);

  @Test
  void should_RevokeOldAndIssueNew_When_RefreshValid() {
    var uid = UserId.newId();
    when(store.validate("raw")).thenReturn(Optional.of(uid));
    var tokens = SessionTokens.bearer("a2", "r2", 900);
    when(sessionIssuer.issueFor(uid)).thenReturn(tokens);

    assertThat(service.refresh("raw")).isEqualTo(tokens);
    verify(store).revoke("raw"); // rotation: old token is single-use
  }

  @Test
  void should_Reject_When_RefreshInvalid() {
    when(store.validate("bad")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.refresh("bad"))
        .isInstanceOf(InvalidRefreshTokenException.class);
    verify(store, never()).revoke("bad");
  }
}
