package com.bookie.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Translates common exceptions into consistent JSON error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public Map<String, String> handleResponseStatus(
      ResponseStatusException ex, HttpServletResponse response) {
    response.setStatus(ex.getStatusCode().value());
    String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
    return Map.of("error", message);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Map<String, String> handleRuntime(RuntimeException ex) {
    return Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Internal server error");
  }

  @ExceptionHandler(IOException.class)
  public ResponseEntity<Map<String, String>> handleIO(
      IOException ex, HttpServletResponse response) {
    // SSE connections use text/event-stream; once the async dispatch is in flight the response
    // content type is already set and we cannot write a JSON body over it. Just close cleanly.
    if (response.isCommitted() || "text/event-stream".equals(response.getContentType())) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", ex.getMessage()));
  }
}
