package com.bookie.compatibility.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.ledger.application.LedgerLifecycle;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.PendingIncomeRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegacyCounterpartyReferencesTest {

  @Mock private ExpenseRepository expenseRepository;
  @Mock private IncomeRepository incomeRepository;
  @Mock private PendingIncomeRepository pendingIncomeRepository;
  @Mock private LedgerLifecycle ledgerLifecycle;

  @InjectMocks private LegacyCounterpartyLedgerReferences ledgerReferences;
  @InjectMocks private LegacyCounterpartyIntakeReferences intakeReferences;

  @Nested
  class Ledger {

    @Test
    void detachesCounterpartyWithoutDeletingFinancialRows() {
      ledgerReferences.detachCounterparty(8L);

      verify(ledgerLifecycle).detachCounterparty(8L);
      verify(expenseRepository).clearPayerById(8L);
      verify(incomeRepository).clearPayerById(8L);
    }

    @Test
    void failsClosedWhileAnyFinancialReferenceRemains() {
      when(incomeRepository.countByPayerId(8L)).thenReturn(1L);

      assertThatThrownBy(() -> ledgerReferences.requireNoReferences(8L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("8");
    }
  }

  @Nested
  class Intake {

    @Test
    void detachesCounterpartyWithoutDeletingPendingRows() {
      intakeReferences.detachCounterparty(8L);

      verify(pendingIncomeRepository).clearPayerById(8L);
    }

    @Test
    void failsClosedWhileAnyPendingReferenceRemains() {
      when(pendingIncomeRepository.countByPayerId(8L)).thenReturn(1L);

      assertThatThrownBy(() -> intakeReferences.requireNoReferences(8L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("8");
    }
  }
}
