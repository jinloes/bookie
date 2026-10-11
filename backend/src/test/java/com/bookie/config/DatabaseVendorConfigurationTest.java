package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

class DatabaseVendorConfigurationTest {

  private final DatabaseVendorConfiguration configuration = new DatabaseVendorConfiguration();

  @Nested
  class PostProcessEnvironment {

    @Test
    void activatesTheSqlServerProfile() {
      MockEnvironment environment =
          new MockEnvironment().withProperty("BOOKIE_DB_VENDOR", " SqlServer ");

      configuration.postProcessEnvironment(environment, new SpringApplication());

      assertThat(environment.getActiveProfiles()).containsExactly("sqlserver");
    }

    @Test
    void staysOnH2ByDefault() {
      MockEnvironment environment = new MockEnvironment();

      configuration.postProcessEnvironment(environment, new SpringApplication());

      assertThat(environment.getActiveProfiles()).isEmpty();
    }

    @Test
    void acceptsAnExplicitH2() {
      MockEnvironment environment = new MockEnvironment().withProperty("BOOKIE_DB_VENDOR", "h2");

      configuration.postProcessEnvironment(environment, new SpringApplication());

      assertThat(environment.getActiveProfiles()).isEmpty();
    }

    @Test
    void treatsABlankValueAsH2() {
      MockEnvironment environment = new MockEnvironment().withProperty("BOOKIE_DB_VENDOR", "  ");

      configuration.postProcessEnvironment(environment, new SpringApplication());

      assertThat(environment.getActiveProfiles()).isEmpty();
    }

    @Test
    void rejectsAnUnknownVendor() {
      MockEnvironment environment =
          new MockEnvironment().withProperty("BOOKIE_DB_VENDOR", "postgres");

      assertThatThrownBy(
              () -> configuration.postProcessEnvironment(environment, new SpringApplication()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("'postgres'");
    }
  }

  @Test
  void runsBeforeConfigDataLoading() {
    assertThat(configuration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }
}
