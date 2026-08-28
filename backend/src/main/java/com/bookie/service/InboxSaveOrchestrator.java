package com.bookie.service;

import com.bookie.intake.application.IntakeJobKickoff;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.model.Expense;
import com.bookie.model.Income;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.model.SavePendingIncomeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Coordinates saving a pending item as a confirmed Expense or Income. The DB transaction runs
 * inside {@link PendingExpenseService}. That transaction also persists the durable external-sync
 * job; this orchestrator only kicks the already-committed job for prompt processing.
 */
@Service
@RequiredArgsConstructor
public class InboxSaveOrchestrator {

  private final PendingExpenseService pendingExpenseService;
  private final IntakeJobKickoff jobKickoff;

  public Expense saveAsExpense(Long pendingId, SavePendingExpenseRequest request) {
    Expense saved = pendingExpenseService.saveAsExpense(pendingId, request);
    kickExternalSync(saved.getSourceType(), saved.getSourceId());
    return saved;
  }

  public Income saveAsIncome(Long pendingId, SavePendingIncomeRequest request) {
    Income saved = pendingExpenseService.saveAsIncome(pendingId, request);
    kickExternalSync(saved.getSourceType(), saved.getSourceId());
    return saved;
  }

  private void kickExternalSync(com.bookie.model.ExpenseSource sourceType, String legacySourceId) {
    if (sourceType == com.bookie.model.ExpenseSource.RECEIPT) {
      jobKickoff.runForSource(sourceType, legacySourceId, BackgroundJobType.MOVE_RECEIPT);
    } else if (sourceType == com.bookie.model.ExpenseSource.OUTLOOK_EMAIL) {
      jobKickoff.runForSource(sourceType, legacySourceId, BackgroundJobType.MOVE_OUTLOOK);
    }
  }
}
