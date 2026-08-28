package com.bookie.ledger.compatibility;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.ledger.application.LedgerParityException;
import com.bookie.ledger.application.LedgerReferenceResolver;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.application.ResolvedLegacyReferences;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionMap;
import com.bookie.ledger.domain.LegacyTransactionTable;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.FinancialCategory;
import com.bookie.model.TransactionDirection;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaLegacyLedgerReadAdapterTest {

  private static final long TRANSACTION_ID = 101L;
  private static final long LEGACY_ID = 84L;
  private static final long ACTIVITY_ID = 4L;
  private static final long NEUTRAL_CATEGORY_ID = 7L;
  private static final long LEGACY_CATEGORY_ID = 30L;
  private static final LocalDate DATE = LocalDate.of(2026, 8, 26);

  @Mock private LedgerTransactionService ledgerTransactionService;
  @Mock private LedgerReferenceResolver referenceResolver;
  @Mock private EntityManager entityManager;

  @InjectMocks private JpaLegacyLedgerReadAdapter readAdapter;

  private FinancialActivity activity;
  private NeutralCategory neutralCategory;
  private FinancialCategory financialCategory;
  private FinancialTransaction transaction;

  @BeforeEach
  void setUp() {
    activity = FinancialActivity.builder().id(ACTIVITY_ID).active(true).build();
    neutralCategory =
        NeutralCategory.builder()
            .id(NEUTRAL_CATEGORY_ID)
            .key("EMPLOYMENT_OTHER_EXPENSE")
            .direction(TransactionDirection.EXPENSE)
            .active(true)
            .build();
    financialCategory =
        FinancialCategory.builder()
            .id(LEGACY_CATEGORY_ID)
            .key("EMPLOYMENT_OTHER_EXPENSE")
            .direction(TransactionDirection.EXPENSE)
            .active(true)
            .build();
    transaction =
        FinancialTransaction.builder()
            .id(TRANSACTION_ID)
            .amount(new BigDecimal("12.34"))
            .direction(TransactionDirection.EXPENSE)
            .date(DATE)
            .description("Synthetic expense")
            .activity(activity)
            .neutralCategory(neutralCategory)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .version(0L)
            .build();
  }

  @Nested
  class AssertExpenseParity {

    @Test
    void acceptsPreservedLegacyCategoryThatCannotBeDerivedFromFinancialCategory() {
      stubUnifiedExpense(ExpenseCategory.SUPPLIES);

      assertThatCode(() -> readAdapter.assertExpenseParity(List.of(legacyExpense())))
          .doesNotThrowAnyException();
    }

    @Test
    void stillFailsClosedWhenPreservedLegacyCategoryDiffers() {
      stubUnifiedExpense(ExpenseCategory.OTHER);

      assertThatThrownBy(() -> readAdapter.assertExpenseParity(List.of(legacyExpense())))
          .isInstanceOf(LedgerParityException.class)
          .hasMessageContaining("Expense differs for legacy ID");
    }
  }

  private Expense legacyExpense() {
    return Expense.builder()
        .id(LEGACY_ID)
        .amount(transaction.getAmount())
        .description(transaction.getDescription())
        .date(transaction.getDate())
        .category(ExpenseCategory.SUPPLIES)
        .activity(activity)
        .financialCategory(financialCategory)
        .build();
  }

  private void stubUnifiedExpense(ExpenseCategory legacyExpenseCategory) {
    LegacyTransactionMap map =
        LegacyTransactionMap.builder()
            .key(new LegacyTransactionKey(LegacyTransactionTable.EXPENSES, LEGACY_ID))
            .transaction(transaction)
            .legacyExpenseCategory(legacyExpenseCategory)
            .canonicalHash("0".repeat(64))
            .mappedAt(LocalDateTime.now())
            .build();
    when(ledgerTransactionService.findAll(TransactionDirection.EXPENSE))
        .thenReturn(List.of(transaction));
    when(ledgerTransactionService.findMapByTransactionId(TRANSACTION_ID)).thenReturn(map);
    when(referenceResolver.resolveForUnified(
            ACTIVITY_ID, NEUTRAL_CATEGORY_ID, null, transaction.getDate()))
        .thenReturn(
            new ResolvedLegacyReferences(activity, neutralCategory, LEGACY_CATEGORY_ID, null));
    when(entityManager.find(FinancialCategory.class, LEGACY_CATEGORY_ID))
        .thenReturn(financialCategory);
  }
}
