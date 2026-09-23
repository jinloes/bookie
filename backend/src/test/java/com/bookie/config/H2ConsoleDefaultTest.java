package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// Reads the main file directly: the test classpath's application.properties shadows it, so a
// Spring context cannot observe the packaged default.
class H2ConsoleDefaultTest {

  @Nested
  class PackagedDefault {

    @Test
    void consoleIsDisabledUnlessExplicitlyOptedIn() throws Exception {
      Properties properties = new Properties();
      try (Reader reader =
          Files.newBufferedReader(
              Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
        properties.load(reader);
      }

      assertThat(properties.getProperty("spring.h2.console.enabled"))
          .isEqualTo("${BOOKIE_H2_CONSOLE_ENABLED:false}");
    }
  }
}
