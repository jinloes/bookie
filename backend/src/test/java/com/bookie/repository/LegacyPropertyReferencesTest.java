package com.bookie.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.ledger.application.LedgerLifecycle;
import com.bookie.model.FinancialCategory;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegacyPropertyReferencesTest {

  @Mock private ExpenseRepository expenseRepository;
  @Mock private IncomeRepository incomeRepository;
  @Mock private PendingIncomeRepository pendingIncomeRepository;
  @Mock private PendingExpenseRepository pendingExpenseRepository;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private FinancialCategoryRepository financialCategoryRepository;
  @Mock private LedgerLifecycle ledgerLifecycle;

  @InjectMocks private LegacyPropertyLedgerReferences ledgerReferences;
  @InjectMocks private LegacyPropertyIntakeReferences intakeReferences;

  @Nested
  class Ledger {

    @Test
    void reassignsBothDirectionsToTheirRequiredFallbackCategories() {
      FinancialActivity replacement = FinancialActivity.builder().id(99L).build();
      FinancialCategory expenseCategory = FinancialCategory.builder().id(20L).build();
      FinancialCategory incomeCategory = FinancialCategory.builder().id(21L).build();
      when(activityCatalog.getNeedsClassification()).thenReturn(replacement);
      when(financialCategoryRepository.findByKey("OTHER_EXPENSE"))
          .thenReturn(Optional.of(expenseCategory));
      when(financialCategoryRepository.findByKey("OTHER_INCOME"))
          .thenReturn(Optional.of(incomeCategory));

      ledgerReferences.reassignActivity(10L);

      verify(ledgerLifecycle).reassignActivity(10L, 99L, 21L, 20L);
      verify(expenseRepository).reassignClassification(10L, replacement, expenseCategory);
      verify(incomeRepository).reassignClassification(10L, replacement, incomeCategory);
    }

    @Test
    void detachesPropertyWithoutDeletingLedgerRows() {
      ledgerReferences.detachProperty(7L);

      verify(expenseRepository).clearPropertyById(7L);
      verify(incomeRepository).clearPropertyById(7L);
    }

    @Test
    void failsClosedWhileAnyPropertyOrRetiredActivityReferenceRemains() {
      when(expenseRepository.countByPropertyId(7L)).thenReturn(1L);

      assertThatThrownBy(() -> ledgerReferences.requireNoReferences(7L, Optional.of(10L)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("7");
    }
  }

  @Nested
  class Intake {

    @Test
    void reassignsPendingIncomeAndExpenseDirectionsSeparately() {
      FinancialActivity replacement = FinancialActivity.builder().id(99L).build();
      FinancialCategory expenseCategory = FinancialCategory.builder().id(20L).build();
      FinancialCategory incomeCategory = FinancialCategory.builder().id(21L).build();
      when(activityCatalog.getNeedsClassification()).thenReturn(replacement);
      when(financialCategoryRepository.findByKey("OTHER_EXPENSE"))
          .thenReturn(Optional.of(expenseCategory));
      when(financialCategoryRepository.findByKey("OTHER_INCOME"))
          .thenReturn(Optional.of(incomeCategory));

      intakeReferences.reassignActivity(10L);

      verify(pendingIncomeRepository).reassignClassification(10L, replacement, incomeCategory);
      verify(pendingExpenseRepository)
          .reassignIncomeClassification(10L, replacement, incomeCategory);
      verify(pendingExpenseRepository)
          .reassignExpenseClassification(10L, replacement, expenseCategory);
    }

    @Test
    void detachesPropertyWithoutDeletingPendingIncomeRows() {
      intakeReferences.detachProperty(7L);

      verify(pendingIncomeRepository).clearPropertyById(7L);
    }

    @Test
    void failsClosedWhileAnyPendingActivityReferenceRemains() {
      when(pendingExpenseRepository.countByActivityId(10L)).thenReturn(1L);

      assertThatThrownBy(() -> intakeReferences.requireNoReferences(7L, Optional.of(10L)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("7");
    }
  }
}
