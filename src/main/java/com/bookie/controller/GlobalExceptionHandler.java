package com.bookie.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Translates common exceptions into consistent JSON error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ApiResponses.ApiErrorResponse handleResponseStatus(
      ResponseStatusException ex, HttpServletResponse response) {
    String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    response.setStatus(status.value());
    return new ApiResponses.ApiErrorResponse(statusCode(status, message), message, Map.of());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponses.ApiErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
    return new ApiResponses.ApiErrorResponse("INVALID_ARGUMENT", ex.getMessage(), Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponses.ApiErrorResponse handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    field ->
                        field.getDefaultMessage() != null
                            ? field.getDefaultMessage()
                            : "Invalid value",
                    (first, second) -> first));
    return new ApiResponses.ApiErrorResponse(
        "BAD_REQUEST", "Validation failed for request body", Map.copyOf(details));
  }

  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponses.ApiErrorResponse handleRuntime(RuntimeException ex) {
    return new ApiResponses.ApiErrorResponse(
        "INTERNAL_ERROR",
        ex.getMessage() != null ? ex.getMessage() : "Internal server error",
        Map.of());
  }

  @ExceptionHandler(IOException.class)
  public ResponseEntity<ApiResponses.ApiErrorResponse> handleIO(
      IOException ex, HttpServletResponse response) {
    // SSE connections use text/event-stream; once the async dispatch is in flight the response
    // content type is already set and we cannot write a JSON body over it. Just close cleanly.
    if (response.isCommitted() || "text/event-stream".equals(response.getContentType())) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiResponses.ApiErrorResponse("IO_ERROR", ex.getMessage(), Map.of()));
  }

  private String statusCode(HttpStatus status, String message) {
    if (status == HttpStatus.UNAUTHORIZED && message != null && message.contains("Outlook")) {
      return "OUTLOOK_AUTH_REQUIRED";
    }
    return switch (status) {
      case BAD_REQUEST -> "BAD_REQUEST";
      case UNAUTHORIZED -> "UNAUTHORIZED";
      case FORBIDDEN -> "FORBIDDEN";
      case NOT_FOUND -> "NOT_FOUND";
      case CONFLICT -> "CONFLICT";
      case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
      default -> "HTTP_" + status.value();
    };
  }
}
