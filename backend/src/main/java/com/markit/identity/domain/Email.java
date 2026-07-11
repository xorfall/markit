package com.markit.identity.domain;

import java.util.regex.Pattern;

/**
 * A normalized, syntactically-valid email address. Validation happens at construction so an invalid
 * {@code Email} cannot exist (boundary validation). Framework-free (NFR-MAINT-001).
 */
public record Email(String value) {

  private static final Pattern PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  public Email {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Email must not be blank");
    }
    value = value.trim().toLowerCase();
    if (!PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid email address");
    }
  }
}
