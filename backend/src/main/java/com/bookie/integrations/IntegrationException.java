package com.bookie.integrations;

import lombok.Builder;
import lombok.Getter;

@Getter
public class IntegrationException extends RuntimeException {

  private final IntegrationFailureKind kind;
  private final Integer statusCode;

  @Builder
  public IntegrationException(
      IntegrationFailureKind kind, String message, Integer statusCode, Throwable cause) {
    super(message, cause);
    this.kind = kind;
    this.statusCode = statusCode;
  }

  public boolean isRetryable() {
    return kind.isRetryable();
  }

  public boolean isManualReviewRequired() {
    return kind.isManualReviewRequired();
  }
}
