package com.markit.bookmarking.domain;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A syntactically-valid http(s) URL. Validated at construction so an invalid {@code Url} cannot
 * exist (boundary validation). Framework-free (NFR-MAINT-001). The raw string is preserved — no
 * normalization (data-model §3: query-param variations are intentionally distinct).
 */
public record Url(String value) {

  public static final int MAX_LENGTH = 2048;

  public Url {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("URL must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("URL must not exceed " + MAX_LENGTH + " characters");
    }
    String scheme = schemeOf(value);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalArgumentException("URL must use the http or https scheme");
    }
  }

  private static String schemeOf(String value) {
    try {
      String scheme = new URI(value).getScheme();
      return scheme == null ? null : scheme.toLowerCase();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("URL is malformed", e);
    }
  }
}
