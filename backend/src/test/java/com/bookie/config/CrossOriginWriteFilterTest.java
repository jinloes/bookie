package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CrossOriginWriteFilterTest {

  private final CrossOriginWriteFilter filter =
      new CrossOriginWriteFilter(
          "http://localhost:5173, tauri://localhost,http://tauri.localhost", new ObjectMapper());

  private MockHttpServletResponse send(String method, String origin) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/backups/key/confirm");
    if (origin != null) {
      request.addHeader("Origin", origin);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    if (chain.getRequest() != null) {
      response.setStatus(204);
    }
    return response;
  }

  @Nested
  class UnsafeMethods {

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void rejectsADisallowedOriginWithTheErrorEnvelope(String method) throws Exception {
      MockHttpServletResponse response = send(method, "https://evil.example");

      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(response.getContentType()).isEqualTo("application/json");
      assertThat(response.getContentAsString())
          .isEqualTo(
              """
              {"code":"FORBIDDEN","message":"Cross-origin request rejected","details":{}}""");
    }

    @Test
    void rejectsTheOpaqueNullOrigin() throws Exception {
      assertThat(send("POST", "null").getStatus()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "tauri://localhost", "http://tauri.localhost"})
    void passesAnAllowedOrigin(String origin) throws Exception {
      assertThat(send("POST", origin).getStatus()).isEqualTo(204);
    }

    @Test
    void passesARequestWithoutAnOrigin() throws Exception {
      assertThat(send("POST", null).getStatus()).isEqualTo(204);
    }
  }

  @Nested
  class SafeMethods {

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void passesEvenFromADisallowedOrigin(String method) throws Exception {
      assertThat(send(method, "https://evil.example").getStatus()).isEqualTo(204);
    }
  }
}
