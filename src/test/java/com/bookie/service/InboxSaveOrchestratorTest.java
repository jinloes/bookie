package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.model.SavePendingIncomeRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboxSaveOrchestratorTest {

  @Mock private PendingExpenseService pendingExpenseService;
  @Mock private ExpenseService expenseService;
  @Mock private IncomeService incomeService;
  @Mock private OutlookService outlookService;
  @Mock private ReceiptService receiptService;

  @InjectMocks private InboxSaveOrchestrator orchestrator;

  @Nested
  class SaveAsExpense {

    @Test
    void outlookEmail_noMove_doesNotUpdateSourceId() {
      Expense saved = new Expense();
      saved.setId(10L);
      saved.setSourceId("msg-123");
      saved.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      saved.setDate(LocalDate.of(2026, 3, 1));
      when(pendingExpenseService.saveAsExpense(eq(1L), any())).thenReturn(saved);
      when(outlookService.moveEmailIfConfigured("msg-123")).thenReturn(Optional.empty());

      Expense result =
          orchestrator.saveAsExpense(
              1L,
              new SavePendingExpenseRequest(
                  BigDecimal.TEN, "Water bill", LocalDate.of(2026, 3, 1), "UTILITIES", null, null));

      assertThat(result.getId()).isEqualTo(10L);
      verify(expenseService, never()).updateSourceId(any(), any());
      verify(receiptService, never()).moveTaxesFolder(any(), anyInt());
    }

    @Test
    void outlookEmail_moved_updatesSourceId() {
      Expense saved = new Expense();
      saved.setId(10L);
      saved.setSourceId("msg-original");
      saved.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      saved.setDate(LocalDate.of(2026, 3, 1));
      when(pendingExpenseService.saveAsExpense(eq(1L), any())).thenReturn(saved);
      when(outlookService.moveEmailIfConfigured("msg-original"))
          .thenReturn(Optional.of("msg-moved"));

      orchestrator.saveAsExpense(
          1L,
          new SavePendingExpenseRequest(
              BigDecimal.TEN, "Water bill", LocalDate.of(2026, 3, 1), "UTILITIES", null, null));

      verify(expenseService).updateSourceId(10L, "msg-moved");
    }

    @Test
    void receipt_movesFile_doesNotCallOutlook() {
      Expense saved = new Expense();
      saved.setId(20L);
      saved.setSourceId("item-receipt");
      saved.setSourceType(ExpenseSource.RECEIPT);
      saved.setDate(LocalDate.of(2026, 4, 1));
      when(pendingExpenseService.saveAsExpense(eq(2L), any())).thenReturn(saved);

      orchestrator.saveAsExpense(
          2L,
          new SavePendingExpenseRequest(
              BigDecimal.TEN, "Receipt", LocalDate.of(2026, 4, 1), "REPAIRS", null, null));

      verify(receiptService).moveTaxesFolder("item-receipt", 2026);
      verify(outlookService, never()).moveEmailIfConfigured(any(String.class));
    }

    @Test
    void outlookEmail_moveFails_doesNotPropagateException() {
      Expense saved = new Expense();
      saved.setId(10L);
      saved.setSourceId("msg-123");
      saved.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      saved.setDate(LocalDate.of(2026, 3, 1));
      when(pendingExpenseService.saveAsExpense(eq(1L), any())).thenReturn(saved);
      when(outlookService.moveEmailIfConfigured("msg-123"))
          .thenThrow(new RuntimeException("Graph API error"));

      Expense result =
          orchestrator.saveAsExpense(
              1L,
              new SavePendingExpenseRequest(
                  BigDecimal.TEN, "Water bill", LocalDate.of(2026, 3, 1), "UTILITIES", null, null));

      assertThat(result.getId()).isEqualTo(10L);
    }
  }

  @Nested
  class SaveAsIncome {

    @Test
    void outlookEmail_moved_updatesSourceId() {
      Income saved = new Income();
      saved.setId(5L);
      saved.setSourceId("msg-rent");
      saved.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      saved.setDate(LocalDate.of(2026, 3, 1));
      when(pendingExpenseService.saveAsIncome(eq(1L), any())).thenReturn(saved);
      when(outlookService.moveEmailIfConfigured("msg-rent")).thenReturn(Optional.of("msg-moved"));

      orchestrator.saveAsIncome(
          1L,
          new SavePendingIncomeRequest(
              BigDecimal.valueOf(1500), "Rent", LocalDate.of(2026, 3, 1), "Jane Smith", null));

      verify(incomeService).updateSourceId(5L, "msg-moved");
    }
  }
}
