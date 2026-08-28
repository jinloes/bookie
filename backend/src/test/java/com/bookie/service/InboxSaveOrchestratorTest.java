package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.IntakeJobKickoff;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.model.SavePendingIncomeRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboxSaveOrchestratorTest {

  @Mock private PendingExpenseService pendingExpenseService;
  @Mock private IntakeJobKickoff jobKickoff;

  @InjectMocks private InboxSaveOrchestrator orchestrator;

  @Nested
  class SaveAsExpense {

    @Test
    void outlookEmail_kicksCommittedMoveJobWithoutRewritingLegacyId() {
      Expense saved = expense(10L, ExpenseSource.OUTLOOK_EMAIL, "legacy-msg");
      when(pendingExpenseService.saveAsExpense(eq(1L), any())).thenReturn(saved);

      Expense result = orchestrator.saveAsExpense(1L, expenseRequest());

      assertThat(result.getSourceId()).isEqualTo("legacy-msg");
      verify(jobKickoff)
          .runForSource(ExpenseSource.OUTLOOK_EMAIL, "legacy-msg", BackgroundJobType.MOVE_OUTLOOK);
    }

    @Test
    void receipt_kicksCommittedMoveJob() {
      Expense saved = expense(20L, ExpenseSource.RECEIPT, "receipt-1");
      when(pendingExpenseService.saveAsExpense(eq(2L), any())).thenReturn(saved);

      orchestrator.saveAsExpense(2L, expenseRequest());

      verify(jobKickoff)
          .runForSource(ExpenseSource.RECEIPT, "receipt-1", BackgroundJobType.MOVE_RECEIPT);
    }

    @Test
    void manualRecord_hasNoExternalJob() {
      Expense saved = expense(30L, ExpenseSource.MANUAL, null);
      when(pendingExpenseService.saveAsExpense(eq(3L), any())).thenReturn(saved);

      orchestrator.saveAsExpense(3L, expenseRequest());

      verify(jobKickoff, never()).runForSource(any(), any(), any());
    }

    @Test
    void failedDatabaseSave_neverKicksAJob() {
      when(pendingExpenseService.saveAsExpense(eq(1L), any()))
          .thenThrow(new IllegalStateException("rollback"));

      assertThatThrownBy(() -> orchestrator.saveAsExpense(1L, expenseRequest()))
          .isInstanceOf(IllegalStateException.class);

      verify(jobKickoff, never()).runForSource(any(), any(), any());
    }
  }

  @Nested
  class SaveAsIncome {

    @Test
    void outlookEmail_kicksCommittedMoveJob() {
      Income saved = new Income();
      saved.setId(5L);
      saved.setSourceId("legacy-rent");
      saved.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      when(pendingExpenseService.saveAsIncome(eq(1L), any())).thenReturn(saved);

      orchestrator.saveAsIncome(1L, incomeRequest());

      verify(jobKickoff)
          .runForSource(ExpenseSource.OUTLOOK_EMAIL, "legacy-rent", BackgroundJobType.MOVE_OUTLOOK);
    }
  }

  private Expense expense(Long id, ExpenseSource source, String sourceId) {
    Expense expense = new Expense();
    expense.setId(id);
    expense.setSourceType(source);
    expense.setSourceId(sourceId);
    return expense;
  }

  private SavePendingExpenseRequest expenseRequest() {
    return new SavePendingExpenseRequest(
        BigDecimal.TEN, "Water bill", LocalDate.of(2026, 3, 1), "UTILITIES", null, null);
  }

  private SavePendingIncomeRequest incomeRequest() {
    return new SavePendingIncomeRequest(
        BigDecimal.valueOf(1500), "Rent", LocalDate.of(2026, 3, 1), "Tenant", null);
  }
}
