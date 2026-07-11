package com.markit.identity.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Security configuration bound from {@code markit.security.*}. */
@ConfigurationProperties(prefix = "markit.security")
public record SecurityProperties(Jwt jwt, Google google) {

  public record Jwt(String secret, Duration accessTokenValidity, Duration refreshTokenValidity) {}

  public record Google(String clientId) {}
}
