package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface IntakeJobDispatcher {

  JobExecutionResult execute(BackgroundJob job);

  default Map<Long, JobExecutionOutcome> executeBatch(List<BackgroundJob> jobs) {
    Map<Long, JobExecutionOutcome> outcomes = new LinkedHashMap<>();
    for (BackgroundJob job : jobs) {
      try {
        outcomes.put(job.getId(), JobExecutionOutcome.succeeded(execute(job)));
      } catch (Exception failure) {
        outcomes.put(job.getId(), JobExecutionOutcome.failed(failure));
      }
    }
    return outcomes;
  }
}
