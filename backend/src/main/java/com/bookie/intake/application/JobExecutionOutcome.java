package com.bookie.intake.application;

public record JobExecutionOutcome(JobExecutionResult result, Exception failure) {

  public JobExecutionOutcome {
    if ((result == null) == (failure == null)) {
      throw new IllegalArgumentException("Exactly one of result or failure is required");
    }
  }

  public static JobExecutionOutcome succeeded(JobExecutionResult result) {
    return new JobExecutionOutcome(result, null);
  }

  public static JobExecutionOutcome failed(Exception failure) {
    return new JobExecutionOutcome(null, failure);
  }

  public boolean successful() {
    return failure == null;
  }
}
