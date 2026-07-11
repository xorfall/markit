package com.markit.bookmarking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.com",
        "https://example.com/path?q=1",
        "HTTPS://Example.com",
        "http://localhost:8080/x"
      })
  void should_Accept_When_HttpOrHttps(String value) {
    assertThat(new Url(value).value()).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void should_Reject_When_Blank(String value) {
    assertThatThrownBy(() -> new Url(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_Reject_When_Null() {
    assertThatThrownBy(() -> new Url(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://example.com", "file:///etc/passwd", "javascript:alert(1)", "example.com"})
  void should_Reject_When_NotHttpScheme(String value) {
    assertThatThrownBy(() -> new Url(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_Reject_When_TooLong() {
    String tooLong = "https://example.com/" + "a".repeat(Url.MAX_LENGTH);

    assertThatThrownBy(() -> new Url(tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2048");
  }

  @Test
  void should_Reject_When_Malformed() {
    assertThatThrownBy(() -> new Url("http://exa mple.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
