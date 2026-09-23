package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// Reads the main file directly: the test classpath's application.properties shadows it, so a
// Spring context cannot observe the packaged default.
class DatasourceUrlDefaultTest {

  @Nested
  class PackagedDefault {

    @Test
    void embeddedDatabaseDoesNotStartAnAutoServerListener() throws Exception {
      Properties properties = new Properties();
      try (Reader reader =
          Files.newBufferedReader(
              Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
        properties.load(reader);
      }

      String url = properties.getProperty("spring.datasource.url");
      assertThat(url)
          .isEqualTo("jdbc:h2:file:${bookie.data-dir}/bookiedb;DB_CLOSE_DELAY=-1")
          .satisfies(
              value -> assertThat(value.toUpperCase(Locale.ROOT)).doesNotContain("AUTO_SERVER"));
    }
  }
}
