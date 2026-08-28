package com.bookie.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionTable;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LedgerCanonicalHashTest {

  @Nested
  class Calculate {

    @Test
    void includesPreservedLegacyExpenseCategory() {
      FinancialActivity activity = FinancialActivity.builder().id(4L).build();
      NeutralCategory category = NeutralCategory.builder().id(7L).build();
      FinancialTransaction transaction =
          FinancialTransaction.builder()
              .amount(new BigDecimal("12.34"))
              .direction(TransactionDirection.EXPENSE)
              .date(LocalDate.of(2026, 8, 26))
              .description("Synthetic expense")
              .activity(activity)
              .neutralCategory(category)
              .build();
      LegacyTransactionKey key = new LegacyTransactionKey(LegacyTransactionTable.EXPENSES, 84L);

      String suppliesHash =
          LedgerCanonicalHash.calculate(key, transaction, ExpenseCategory.SUPPLIES);
      String otherHash = LedgerCanonicalHash.calculate(key, transaction, ExpenseCategory.OTHER);

      assertThat(suppliesHash).matches("[0-9a-f]{64}");
      assertThat(otherHash).matches("[0-9a-f]{64}").isNotEqualTo(suppliesHash);
    }
  }
}
