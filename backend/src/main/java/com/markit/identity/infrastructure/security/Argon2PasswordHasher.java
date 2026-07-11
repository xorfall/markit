package com.markit.identity.infrastructure.security;

import com.markit.identity.application.port.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** Argon2id password hashing (NFR-SEC-003), backed by Spring Security's encoder. */
@Component
public class Argon2PasswordHasher implements PasswordHasher {

  private final Argon2PasswordEncoder encoder =
      Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String storedHash) {
    return encoder.matches(rawPassword, storedHash);
  }
}
