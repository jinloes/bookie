package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

// src/test/resources/application.properties narrows cors.allowed-origins to the Vite origin, so
// the preflight tests restore the production default; DefaultAllowedOrigins pins the two together.
@WebMvcTest(
    controllers = HealthController.class,
    properties = "cors.allowed-origins=" + WebConfigCorsTest.PRODUCTION_DEFAULT_ORIGINS)
class WebConfigCorsTest {

  static final String PRODUCTION_DEFAULT_ORIGINS =
      "http://localhost:5173,tauri://localhost,http://tauri.localhost,https://tauri.localhost";

  private static final Pattern PLACEHOLDER_DEFAULT = Pattern.compile("^\\$\\{[^:}]+:(.*)}$");

  @Autowired private MockMvc mockMvc;

  private ResultActions preflight(String origin) throws Exception {
    return mockMvc.perform(
        options("/api/health")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "content-type,x-request-id"));
  }

  @Nested
  class Preflight {

    @Test
    void acceptsWindowsTauriOriginWithRequestIdHeader() throws Exception {
      assertAllowsRequestIdHeader("http://tauri.localhost");
    }

    @Test
    void acceptsMacTauriOriginWithRequestIdHeader() throws Exception {
      assertAllowsRequestIdHeader("tauri://localhost");
    }

    @Test
    void rejectsUnlistedOrigin() throws Exception {
      preflight("https://evil.example")
          .andExpect(status().isForbidden())
          .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private void assertAllowsRequestIdHeader(String origin) throws Exception {
      // Spring answers 200 with only the allowed subset of requested headers; the browser then
      // fails the preflight if x-request-id is missing from Access-Control-Allow-Headers.
      String allowHeaders =
          preflight(origin)
              .andExpect(status().isOk())
              .andExpect(header().string("Access-Control-Allow-Origin", origin))
              .andReturn()
              .getResponse()
              .getHeader("Access-Control-Allow-Headers");

      assertThat(allowHeaders).containsIgnoringCase("x-request-id");
    }
  }

  @Nested
  class DefaultAllowedOrigins {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "http://localhost:5173",
          "tauri://localhost",
          "http://tauri.localhost",
          "https://tauri.localhost"
        })
    void acceptsPackagedAndDevOriginsWithTheRequestIdHeader(String origin) throws Exception {
      CorsConfiguration config = apiCorsConfiguration(productionDefaultOrigins());

      assertThat(config.checkOrigin(origin)).isEqualTo(origin);
      assertThat(config.checkHeaders(List.of("content-type", "x-request-id")))
          .containsExactlyInAnyOrder("content-type", "x-request-id");
    }

    @Test
    void rejectsUnlistedOrigin() throws Exception {
      CorsConfiguration config = apiCorsConfiguration(productionDefaultOrigins());

      assertThat(config.checkOrigin("https://evil.example")).isNull();
    }

    @Test
    void matchesTheOriginsUsedByThePreflightTests() throws Exception {
      assertThat(productionDefaultOrigins()).isEqualTo(PRODUCTION_DEFAULT_ORIGINS);
    }
  }

  private static String productionDefaultOrigins() throws Exception {
    Properties properties = new Properties();
    try (Reader reader =
        Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
      properties.load(reader);
    }
    Matcher matcher =
        PLACEHOLDER_DEFAULT.matcher(properties.getProperty("cors.allowed-origins").trim());
    assertThat(matcher.matches()).isTrue();
    return matcher.group(1);
  }

  private static CorsConfiguration apiCorsConfiguration(String allowedOrigins) {
    WebConfig webConfig = new WebConfig();
    ReflectionTestUtils.setField(webConfig, "allowedOrigins", allowedOrigins);
    ExposedCorsRegistry registry = new ExposedCorsRegistry();
    webConfig.addCorsMappings(registry);
    return registry.configurations().get("/api/**");
  }

  private static class ExposedCorsRegistry extends CorsRegistry {
    Map<String, CorsConfiguration> configurations() {
      return getCorsConfigurations();
    }
  }
}
