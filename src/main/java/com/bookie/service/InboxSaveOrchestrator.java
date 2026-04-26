package com.bookie.service;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.model.SavePendingIncomeRequest;
import java.time.LocalDate;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Coordinates saving a pending item as a confirmed Expense or Income. The DB transaction runs
 * inside {@link PendingExpenseService}. External side-effects (email move, receipt file move) run
 * here after the transaction commits, so a DB commit can never be rolled back by a failed
 * irreversible external call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxSaveOrchestrator {

  private final PendingExpenseService pendingExpenseService;
  private final ExpenseService expenseService;
  private final IncomeService incomeService;
  private final OutlookService outlookService;
  private final ReceiptService receiptService;

  public Expense saveAsExpense(Long pendingId, SavePendingExpenseRequest request) {
    Expense saved = pendingExpenseService.saveAsExpense(pendingId, request);
    applyPostSaveEffects(
        saved.getSourceId(),
        saved.getSourceType(),
        saved.getDate(),
        newId -> expenseService.updateSourceId(saved.getId(), newId));
    return saved;
  }

  public Income saveAsIncome(Long pendingId, SavePendingIncomeRequest request) {
    Income saved = pendingExpenseService.saveAsIncome(pendingId, request);
    applyPostSaveEffects(
        saved.getSourceId(),
        saved.getSourceType(),
        saved.getDate(),
        newId -> incomeService.updateSourceId(saved.getId(), newId));
    return saved;
  }

  private void applyPostSaveEffects(
      String sourceId, ExpenseSource sourceType, LocalDate date, Consumer<String> onNewId) {
    if (sourceType == ExpenseSource.RECEIPT) {
      receiptService.moveTaxesFolder(sourceId, date.getYear());
    } else if (sourceType == ExpenseSource.OUTLOOK_EMAIL) {
      try {
        outlookService.moveEmailIfConfigured(sourceId).ifPresent(onNewId);
      } catch (Exception e) {
        log.error(
            "Email move failed for sourceId={} — record is saved, move can be retried manually: {}",
            sourceId,
            e.getMessage());
      }
    }
  }
}
