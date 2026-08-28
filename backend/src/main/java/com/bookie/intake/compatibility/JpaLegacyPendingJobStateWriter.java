package com.bookie.intake.compatibility;

import com.bookie.intake.application.LegacyPendingJobStateWriter;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.LegacyPendingTable;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.repository.PendingExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaLegacyPendingJobStateWriter implements LegacyPendingJobStateWriter {

  private final PendingExpenseRepository pendingExpenseRepository;

  @Override
  public void parsingStarted(BackgroundJob job) {
    update(job, PendingExpenseStatus.PROCESSING, null);
  }

  @Override
  public void parsingRequeued(BackgroundJob job) {
    update(job, PendingExpenseStatus.PROCESSING, null);
  }

  @Override
  public void parsingFailed(BackgroundJob job, String errorMessage) {
    update(job, PendingExpenseStatus.FAILED, errorMessage);
  }

  private void update(BackgroundJob job, PendingExpenseStatus status, String errorMessage) {
    if (job.getLegacyPendingTable() != LegacyPendingTable.PENDING_EXPENSES
        || job.getLegacyPendingId() == null) {
      return;
    }
    pendingExpenseRepository
        .findById(job.getLegacyPendingId())
        .ifPresent(
            pending -> {
              pending.setStatus(status);
              pending.setErrorMessage(errorMessage);
              pendingExpenseRepository.save(pending);
            });
  }
}
