package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.Income;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.Property;
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

@ExtendWith(MockitoExtension.class)
class PendingExpenseServiceTest {

  @Mock private PendingExpenseRepository pendingRepository;
  @Mock private ExpenseService expenseService;
  @Mock private IncomeService incomeService;
  @Mock private PropertyRepository propertyRepository;
  @Mock private PayerRepository payerRepository;
  @Mock private PayerService payerService;

  @InjectMocks private PendingExpenseService service;

  @Nested
  class SaveAsIncome {

    @Test
    void savesIncomeWithAllFields() {
      PendingExpense pending = new PendingExpense();
      pending.setId(1L);
      pending.setSourceId("msg-abc");
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
  }
}
