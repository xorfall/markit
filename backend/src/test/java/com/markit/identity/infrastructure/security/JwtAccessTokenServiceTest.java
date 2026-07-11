package com.markit.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.markit.identity.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtAccessTokenServiceTest {

  private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-0123456789";

  private static SecurityProperties props() {
    return new SecurityProperties(
        new SecurityProperties.Jwt(SECRET, Duration.ofMinutes(15), Duration.ofDays(30)),
        new SecurityProperties.Google(""));
  }

  private static JwtAccessTokenService serviceAt(Instant now) {
    return new JwtAccessTokenService(props(), Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void should_RoundTrip_When_TokenValid() {
    var service = serviceAt(Instant.parse("2026-07-11T00:00:00Z"));
    var userId = UserId.newId();

    var token = service.issue(userId);

    assertThat(service.verify(token)).contains(userId);
  }

  @Test
  void should_ReturnEmpty_When_TokenGarbage() {
    var service = serviceAt(Instant.parse("2026-07-11T00:00:00Z"));
    assertThat(service.verify("not.a.jwt")).isEmpty();
  }

  @Test
  void should_ReturnEmpty_When_TokenExpired() {
    var issued = Instant.parse("2026-07-11T00:00:00Z");
    var token = serviceAt(issued).issue(UserId.newId());

    // Verify 16 minutes later — past the 15-minute validity.
    var later = serviceAt(issued.plus(Duration.ofMinutes(16)));
    assertThat(later.verify(token)).isEmpty();
  }
}
