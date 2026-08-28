package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.model.ExpenseSource;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IntakeJobKickoff {

  private final DurableBackgroundJobWorker worker;

  @Async
  public void runForSource(ExpenseSource origin, String legacySourceId, BackgroundJobType type) {
    worker.runAvailableForSource(origin, legacySourceId, type);
  }

  @Async
  public void runJob(Long jobId) {
    worker.runAvailableJob(jobId);
  }
}
