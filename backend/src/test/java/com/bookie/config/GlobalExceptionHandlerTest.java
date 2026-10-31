package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.mock.web.MockHttpServletResponse;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @BeforeEach
  void setRequestId() {
    MDC.put("requestId", "5a61449c-0000-0000-0000-000000000000");
  }

  @AfterEach
  void clearRequestId() {
    MDC.clear();
  }

  @Nested
  class HandleRuntime {

    @Test
    void hidesTheExceptionMessageAndKeepsTheRequestReference() {
      RuntimeException sqlFailure =
          new UncategorizedSQLException(
              "PreparedStatementCallback",
              "SELECT mapping.legacy_category_id FROM category_reporting_mappings",
              new java.sql.SQLException("Invalid column name 'TRUE'."));

      ApiErrorResponse response = handler.handleRuntime(sqlFailure);

      assertThat(response.code()).isEqualTo("INTERNAL_ERROR");
      assertThat(response.message()).isEqualTo(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE);
      assertThat(response.details())
          .isEqualTo(Map.of("requestId", "5a61449c-0000-0000-0000-000000000000"));
    }

    @Test
    void omitsTheReferenceWhenNoRequestIdIsBound() {
      MDC.clear();

      ApiErrorResponse response = handler.handleRuntime(new IllegalStateException());

      assertThat(response.message()).isEqualTo(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE);
      assertThat(response.details()).isEmpty();
    }
  }

  @Nested
  class HandleIo {

    @Test
    void hidesTheExceptionMessage() {
      var response =
          handler.handleIO(
              new IOException("C:\\Users\\someone\\secret.pdf (Access is denied)"),
              new MockHttpServletResponse());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().code()).isEqualTo("IO_ERROR");
      assertThat(response.getBody().message()).isEqualTo(GlobalExceptionHandler.IO_ERROR_MESSAGE);
      assertThat(response.getBody().details()).containsKey("requestId");
    }

    @Test
    void returnsNoBodyForAnEventStream() {
      MockHttpServletResponse servletResponse = new MockHttpServletResponse();
      servletResponse.setContentType("text/event-stream");

      var response = handler.handleIO(new IOException("Broken pipe"), servletResponse);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody()).isNull();
    }

    @Test
    void returnsNoBodyOnceTheResponseIsCommitted() throws IOException {
      MockHttpServletResponse servletResponse = new MockHttpServletResponse();
      servletResponse.flushBuffer();

      var response = handler.handleIO(new IOException("Broken pipe"), servletResponse);

      assertThat(response.getBody()).isNull();
    }
  }
}
