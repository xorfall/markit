package com.markit.identity.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Security configuration (JWT secret + token validities), bound from {@code markit.security.*}. */
@ConfigurationProperties(prefix = "markit.security")
public record SecurityProperties(Jwt jwt) {

  public record Jwt(String secret, Duration accessTokenValidity, Duration refreshTokenValidity) {}
}
