package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.Property;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.model.SavePendingIncomeRequest;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.bookie.repository.PropertyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PendingExpenseServiceTest {

  @Mock private PendingExpenseRepository pendingRepository;
  @Mock private ExpenseService expenseService;
  @Mock private IncomeService incomeService;
  @Mock private PropertyRepository propertyRepository;
  @Mock private PayerRepository payerRepository;
  @Mock private PayerService payerService;
  @Mock private OutlookService outlookService;

  @InjectMocks private PendingExpenseService service;

  @Nested
  class FindOrCreate {

    @Test
    void notFound_createsNewEntry() {
      when(pendingRepository.findBySourceId("src-1")).thenReturn(Optional.empty());
      PendingExpense created = new PendingExpense();
      created.setId(10L);
      created.setStatus(PendingExpenseStatus.PROCESSING);
      when(pendingRepository.save(any())).thenReturn(created);

      var result = service.findOrCreate("src-1", ExpenseSource.OUTLOOK_EMAIL, "subject");

      assertThat(result.alreadyProcessing()).isFalse();
      assertThat(result.pending().getId()).isEqualTo(10L);
    }

    @Test
    void foundAndProcessing_returnsExistingWithoutCreating() {
      PendingExpense existing = new PendingExpense();
      existing.setId(7L);
      existing.setStatus(PendingExpenseStatus.PROCESSING);
      when(pendingRepository.findBySourceId("src-1")).thenReturn(Optional.of(existing));

      var result = service.findOrCreate("src-1", ExpenseSource.OUTLOOK_EMAIL, "subject");

      assertThat(result.alreadyProcessing()).isTrue();
      assertThat(result.pending().getId()).isEqualTo(7L);
      verify(pendingRepository, never()).deleteById(any());
      verify(pendingRepository, never()).save(any());
    }

    @Test
    void foundAndReady_dismissesAndCreatesNew() {
      PendingExpense stale = new PendingExpense();
      stale.setId(5L);
      stale.setStatus(PendingExpenseStatus.READY);
      when(pendingRepository.findBySourceId("src-1")).thenReturn(Optional.of(stale));
      PendingExpense created = new PendingExpense();
      created.setId(11L);
      created.setStatus(PendingExpenseStatus.PROCESSING);
      when(pendingRepository.save(any())).thenReturn(created);

      var result = service.findOrCreate("src-1", ExpenseSource.OUTLOOK_EMAIL, "subject");

      assertThat(result.alreadyProcessing()).isFalse();
      assertThat(result.pending().getId()).isEqualTo(11L);
      verify(pendingRepository).deleteById(5L);
    }
  }

  @Nested
  class SaveAsExpense {

    @Test
    void savesExpenseAndDeletesPending() {
      PendingExpense pending = new PendingExpense();
      pending.setId(1L);
      pending.setSourceId("msg-abc");
      pending.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      pending.setStatus(PendingExpenseStatus.READY);
      when(pendingRepository.findById(1L)).thenReturn(Optional.of(pending));

      Property property = new Property();
      property.setId(10L);
      when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
      when(payerRepository.findById(20L)).thenReturn(Optional.empty());

      Expense saved = new Expense();
      saved.setId(99L);
      saved.setDate(LocalDate.of(2026, 3, 1));
      when(expenseService.save(any())).thenReturn(saved);

      SavePendingExpenseRequest request =
          new SavePendingExpenseRequest(
              BigDecimal.valueOf(500),
              "Plumbing repair",
              LocalDate.of(2026, 3, 1),
              "REPAIRS",
              10L,
              20L);

      Expense result = service.saveAsExpense(1L, request);

      assertThat(result.getId()).isEqualTo(99L);
      verify(pendingRepository).deleteById(1L);
    }

    @Test
    void receiptSource_setsReceiptFields() {
      PendingExpense pending = new PendingExpense();
      pending.setId(3L);
      pending.setSourceId("item-onedrive-123");
      pending.setSubject("receipt.pdf");
      pending.setSourceType(ExpenseSource.RECEIPT);
      pending.setStatus(PendingExpenseStatus.READY);
      when(pendingRepository.findById(3L)).thenReturn(Optional.of(pending));

      when(expenseService.save(any(Expense.class)))
          .thenAnswer(
              inv -> {
                Expense e = inv.getArgument(0);
                e.setId(10L);
                e.setDate(LocalDate.of(2026, 4, 1));
                return e;
              });

      SavePendingExpenseRequest request =
          new SavePendingExpenseRequest(
              BigDecimal.valueOf(300),
              "Plumbing repair",
              LocalDate.of(2026, 4, 1),
              "REPAIRS",
              null,
              null);

      Expense result = service.saveAsExpense(3L, request);

      assertThat(result.getReceiptOneDriveId()).isEqualTo("item-onedrive-123");
      assertThat(result.getReceiptFileName()).isEqualTo("receipt.pdf");
      verify(pendingRepository).deleteById(3L);
    }

    @Test
    void emailSource_autoMoveEnabledWithNoFolder_throwsBeforeSave() {
      PendingExpense pending = new PendingExpense();
      pending.setId(6L);
      pending.setSourceId("msg-email-000");
      pending.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      pending.setStatus(PendingExpenseStatus.READY);
      when(pendingRepository.findById(6L)).thenReturn(Optional.of(pending));

      doThrow(
              new org.springframework.web.server.ResponseStatusException(
                  org.springframework.http.HttpStatus.BAD_REQUEST,
                  "Auto-move is enabled but no destination folder is configured"))
          .when(outlookService)
          .validateEmailAutoMove(ExpenseSource.OUTLOOK_EMAIL);

      SavePendingExpenseRequest request =
          new SavePendingExpenseRequest(
              BigDecimal.valueOf(200),
              "Water bill",
              LocalDate.of(2026, 4, 1),
              "UTILITIES",
              null,
              null);

      assertThatThrownBy(() -> service.saveAsExpense(6L, request))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
          .satisfies(
              ex ->
                  assertThat(
                          ((org.springframework.web.server.ResponseStatusException) ex)
                              .getStatusCode())
                      .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));

      verify(expenseService, never()).save(any());
    }

    @Test
    void pendingNotFound_throwsResponseStatusException() {
      when(pendingRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  service.saveAsExpense(
                      99L,
                      new SavePendingExpenseRequest(
                          BigDecimal.ONE, "desc", LocalDate.now(), "REPAIRS", null, null)))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              ex ->
                  assertThat(((ResponseStatusException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.NOT_FOUND));
    }
  }

  @Nested
  class SaveAsIncome {

    @Test
    void savesIncomeAndDeletesPending() {
      PendingExpense pending = new PendingExpense();
      pending.setId(1L);
      pending.setSourceId("msg-abc");
      pending.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      pending.setStatus(PendingExpenseStatus.READY);
      when(pendingRepository.findById(1L)).thenReturn(Optional.of(pending));

      Property property = new Property();
      property.setId(10L);
      when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

      Income saved = new Income();
      saved.setId(99L);
      when(incomeService.save(any())).thenReturn(saved);

      SavePendingIncomeRequest request =
          new SavePendingIncomeRequest(
              BigDecimal.valueOf(1500),
              "Jane Smith - Rent Mar 2026",
              LocalDate.of(2026, 3, 1),
              "Jane Smith",
              10L);

      Income result = service.saveAsIncome(1L, request);

      assertThat(result.getId()).isEqualTo(99L);
      verify(pendingRepository).deleteById(1L);
    }

    @Test
    void nullPropertyId_savesWithNullProperty() {
      PendingExpense pending = new PendingExpense();
      pending.setId(2L);
      pending.setSourceId("msg-xyz");
      pending.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      pending.setStatus(PendingExpenseStatus.READY);
      when(pendingRepository.findById(2L)).thenReturn(Optional.of(pending));

      Income saved = new Income();
      when(incomeService.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

      SavePendingIncomeRequest request =
          new SavePendingIncomeRequest(
              BigDecimal.valueOf(500), "Deposit", LocalDate.of(2026, 3, 1), "Jane Smith", null);

      Income result = service.saveAsIncome(2L, request);

      assertThat(result.getProperty()).isNull();
      assertThat(result.getSourceId()).isEqualTo("msg-xyz");
    }

    @Test
    void receiptSource_setsReceiptFields() {
      PendingExpense pending = new PendingExpense();
      pending.setId(3L);
      pending.setSourceId("item-onedrive-123");
      pending.setSubject("rent_receipt.pdf");
      pending.setSourceType(ExpenseSource.RECEIPT);
      pending.setStatus(PendingExpenseStatus.READY);
      when(pendingRepository.findById(3L)).thenReturn(Optional.of(pending));

      when(incomeService.save(any(Income.class))).thenAnswer(inv -> inv.getArgument(0));

      SavePendingIncomeRequest request =
          new SavePendingIncomeRequest(
              BigDecimal.valueOf(1200),
              "Jane Smith - Rent Apr 2026",
              LocalDate.of(2026, 4, 1),
              "Jane Smith",
              null);

      Income result = service.saveAsIncome(3L, request);

      assertThat(result.getReceiptOneDriveId()).isEqualTo("item-onedrive-123");
      assertThat(result.getReceiptFileName()).isEqualTo("rent_receipt.pdf");
      verify(pendingRepository).deleteById(3L);
    }

    @Test
    void emailSource_autoMoveEnabledWithNoFolder_throwsBeforeSave() {
      PendingExpense pending = new PendingExpense();
      pending.setId(6L);
      pending.setSourceId("msg-email-000");
      pending.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      pending.setStatus(PendingExpenseStatus.READY);
      when(pendingRepository.findById(6L)).thenReturn(Optional.of(pending));

      doThrow(
              new org.springframework.web.server.ResponseStatusException(
                  org.springframework.http.HttpStatus.BAD_REQUEST,
                  "Auto-move is enabled but no destination folder is configured"))
          .when(outlookService)
          .validateEmailAutoMove(ExpenseSource.OUTLOOK_EMAIL);

      SavePendingIncomeRequest request =
          new SavePendingIncomeRequest(
              BigDecimal.valueOf(800), "Rent", LocalDate.of(2026, 4, 1), "Bob", null);

      assertThatThrownBy(() -> service.saveAsIncome(6L, request))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
          .satisfies(
              ex ->
                  assertThat(
                          ((org.springframework.web.server.ResponseStatusException) ex)
                              .getStatusCode())
                      .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));

      verify(incomeService, never()).save(any());
    }

    @Test
    void pendingNotFound_throwsResponseStatusException() {
      when(pendingRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  service.saveAsIncome(
                      99L,
                      new SavePendingIncomeRequest(
                          BigDecimal.ONE, "desc", LocalDate.now(), "Bob", null)))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              ex ->
                  assertThat(((ResponseStatusException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.NOT_FOUND));
    }
  }

  @Nested
  class ResetForRetry {

    @Test
    void failedItem_resetsToProcessing() {
      PendingExpense pending = new PendingExpense();
      pending.setId(1L);
      pending.setStatus(PendingExpenseStatus.FAILED);
      pending.setErrorMessage("timeout");
      when(pendingRepository.findById(1L)).thenReturn(Optional.of(pending));
      when(pendingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      PendingExpense result = service.resetForRetry(1L);

      assertThat(result.getStatus()).isEqualTo(PendingExpenseStatus.PROCESSING);
      assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void alreadyProcessing_throwsConflict() {
      PendingExpense pending = new PendingExpense();
      pending.setId(2L);
      pending.setStatus(PendingExpenseStatus.PROCESSING);
      when(pendingRepository.findById(2L)).thenReturn(Optional.of(pending));

      assertThatThrownBy(() -> service.resetForRetry(2L))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              ex ->
                  assertThat(((ResponseStatusException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void notFound_throwsNotFound() {
      when(pendingRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.resetForRetry(99L))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              ex ->
                  assertThat(((ResponseStatusException) ex).getStatusCode())
                      .isEqualTo(HttpStatus.NOT_FOUND));
    }
  }
}
