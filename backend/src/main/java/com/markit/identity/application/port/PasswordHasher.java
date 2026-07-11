package com.markit.identity.application.port;

/** Hashes and verifies passwords with a memory-hard function (Argon2id — NFR-SEC-003). */
public interface PasswordHasher {

  String hash(String rawPassword);

  boolean matches(String rawPassword, String storedHash);
}
