package com.markit.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

  @Test
  void should_NormalizeToLowercaseAndTrim_When_Constructed() {
    assertThat(new Email("  User@Example.COM ").value()).isEqualTo("user@example.com");
  }

  @Test
  void should_Reject_When_Blank() {
    assertThatThrownBy(() -> new Email("  ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_Reject_When_MalformedFormat() {
    assertThatThrownBy(() -> new Email("not-an-email")).isInstanceOf(IllegalArgumentException.class);
  }
}
