package com.bookie.intake.application;

public record JobExecutionResult(String immutableSourceId) {

  public static JobExecutionResult completed() {
    return new JobExecutionResult(null);
  }

  public static JobExecutionResult withImmutableSourceId(String immutableSourceId) {
    return new JobExecutionResult(immutableSourceId);
  }
}
