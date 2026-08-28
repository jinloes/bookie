package com.bookie.integrations;

public enum IntegrationFailureKind {
  RECONNECT_REQUIRED(false, true),
  NOT_FOUND(false, true),
  RATE_LIMITED(true, false),
  TRANSIENT(true, false),
  CONFLICT(false, true),
  INVALID_RESPONSE(false, true),
  TERMINAL(false, true);

  private final boolean retryable;
  private final boolean manualReviewRequired;

  IntegrationFailureKind(boolean retryable, boolean manualReviewRequired) {
    this.retryable = retryable;
    this.manualReviewRequired = manualReviewRequired;
  }

  public boolean isRetryable() {
    return retryable;
  }

  public boolean isManualReviewRequired() {
    return manualReviewRequired;
  }
}
