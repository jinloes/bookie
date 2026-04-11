package com.bookie.controller;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Translates common exceptions into consistent JSON error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public Map<String, String> handleResponseStatus(
      ResponseStatusException ex, jakarta.servlet.http.HttpServletResponse response) {
    response.setStatus(ex.getStatusCode().value());
    String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
    return Map.of("error", message);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(IOException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Map<String, String> handleIO(IOException ex) {
    return Map.of("error", ex.getMessage());
  }
}
