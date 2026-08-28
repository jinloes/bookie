package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;

public interface LegacyPendingJobStateWriter {

  void parsingStarted(BackgroundJob job);

  void parsingRequeued(BackgroundJob job);

  void parsingFailed(BackgroundJob job, String errorMessage);
}
