package com.bookie.intake.application;

import lombok.Getter;

@Getter
public class JobExecutionException extends RuntimeException {

  private final FailureKind kind;

  public JobExecutionException(FailureKind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
  }

  public static JobExecutionException retryable(String message, Throwable cause) {
    return new JobExecutionException(FailureKind.RETRYABLE, message, cause);
  }

  public static JobExecutionException manualReview(String message, Throwable cause) {
    return new JobExecutionException(FailureKind.MANUAL_REVIEW, message, cause);
  }

  public static JobExecutionException terminal(String message, Throwable cause) {
    return new JobExecutionException(FailureKind.TERMINAL, message, cause);
  }

  public enum FailureKind {
    RETRYABLE,
    MANUAL_REVIEW,
    TERMINAL
  }
}
