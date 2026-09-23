package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoopbackHostFilterTest {

  private static final String DEFAULT_ALLOWED_HOSTS = "localhost,127.0.0.1,::1";

  @Nested
  class DoFilter {

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST", "127.0.0.1", "::1", "[::1]", " localhost "})
    void loopbackHostnamesReachTheChain(String host) throws Exception {
      MockFilterChain chain = new MockFilterChain();
      MockHttpServletResponse response = new MockHttpServletResponse();

      new LoopbackHostFilter(DEFAULT_ALLOWED_HOSTS).doFilter(request(host), response, chain);

      assertThat(chain.getRequest()).isNotNull();
      assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void portIsIgnoredBecauseOnlyTheHostnameIsCompared() throws Exception {
      MockHttpServletRequest request = request("localhost");
      request.setServerPort(5173);
      MockFilterChain chain = new MockFilterChain();

      new LoopbackHostFilter(DEFAULT_ALLOWED_HOSTS)
          .doFilter(request, new MockHttpServletResponse(), chain);

      assertThat(chain.getRequest()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"evil.example", "localhost.evil.example", "192.168.1.10"})
    void nonLoopbackHostnamesAreRejectedWithoutCallingTheChain(String host) throws Exception {
      assertForbidden(DEFAULT_ALLOWED_HOSTS, host);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingHostnameIsRejectedWithoutCallingTheChain(String host) throws Exception {
      assertForbidden(DEFAULT_ALLOWED_HOSTS, host);
    }
  }

  @Nested
  class AllowedHostsConfiguration {

    @Test
    void customHostnameIsHonoredAfterTrimmingAndLowercasing() throws Exception {
      MockFilterChain chain = new MockFilterChain();

      new LoopbackHostFilter("localhost, Bookie.Local ,")
          .doFilter(request("bookie.local"), new MockHttpServletResponse(), chain);

      assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void hostsOmittedFromCustomListAreRejected() throws Exception {
      assertForbidden("bookie.local", "localhost");
    }

    @Test
    void blankEntriesDoNotAllowAMissingHostname() throws Exception {
      assertForbidden(" , ,", "");
    }
  }

  private static MockHttpServletRequest request(String host) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/properties");
    request.setServerName(host);
    return request;
  }

  private static void assertForbidden(String allowedHosts, String host) throws Exception {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse response = new MockHttpServletResponse();

    new LoopbackHostFilter(allowedHosts).doFilter(request(host), response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"Forbidden host\"}");
  }
}
