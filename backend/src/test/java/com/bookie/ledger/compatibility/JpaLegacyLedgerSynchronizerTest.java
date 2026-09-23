package com.bookie.ledger.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.application.LegacyTransactionSnapshot;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionTable;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Income;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaLegacyLedgerSynchronizerTest {

  @Mock private LedgerTransactionService ledgerTransactionService;

  @InjectMocks private JpaLegacyLedgerSynchronizer synchronizer;

  @Captor private ArgumentCaptor<LegacyTransactionSnapshot> snapshotCaptor;

  @Nested
  class SynchronizeIncome {

    @Test
    void mirrorsOutlookMessageIdAsParentExternalId() {
      synchronizer.synchronize(income("message-001"));

      verify(ledgerTransactionService).synchronizeLegacy(snapshotCaptor.capture());
      assertThat(snapshotCaptor.getValue().getKey())
          .isEqualTo(new LegacyTransactionKey(LegacyTransactionTable.INCOMES, 11L));
      assertThat(snapshotCaptor.getValue().getOrigin()).isEqualTo("OUTLOOK_EMAIL");
      assertThat(snapshotCaptor.getValue().getExternalId()).isEqualTo("message-001:attachment-1");
      assertThat(snapshotCaptor.getValue().getParentExternalId()).isEqualTo("message-001");
    }

    @Test
    void leavesParentExternalIdEmptyWhenLegacyValueIsNull() {
      synchronizer.synchronize(income(null));

      verify(ledgerTransactionService).synchronizeLegacy(snapshotCaptor.capture());
      assertThat(snapshotCaptor.getValue().getParentExternalId()).isNull();
    }
  }

  @Nested
  class SynchronizeExpense {

    @Test
    void mirrorsOutlookMessageIdAsParentExternalId() {
      synchronizer.synchronize(expense("message-002"));

      verify(ledgerTransactionService).synchronizeLegacy(snapshotCaptor.capture());
      assertThat(snapshotCaptor.getValue().getKey())
          .isEqualTo(new LegacyTransactionKey(LegacyTransactionTable.EXPENSES, 12L));
      assertThat(snapshotCaptor.getValue().getOrigin()).isEqualTo("OUTLOOK_EMAIL");
      assertThat(snapshotCaptor.getValue().getExternalId()).isEqualTo("message-002:attachment-1");
      assertThat(snapshotCaptor.getValue().getParentExternalId()).isEqualTo("message-002");
    }

    @Test
    void leavesParentExternalIdEmptyWhenLegacyValueIsNull() {
      synchronizer.synchronize(expense(null));

      verify(ledgerTransactionService).synchronizeLegacy(snapshotCaptor.capture());
      assertThat(snapshotCaptor.getValue().getParentExternalId()).isNull();
    }
  }

  private Income income(String outlookMessageId) {
    return Income.builder()
        .id(11L)
        .amount(new BigDecimal("100.00"))
        .description("Rent")
        .date(LocalDate.of(2026, 8, 1))
        .sourceType(ExpenseSource.OUTLOOK_EMAIL)
        .sourceId("message-001:attachment-1")
        .outlookMessageId(outlookMessageId)
        .activity(FinancialActivity.builder().id(4L).build())
        .financialCategory(FinancialCategory.builder().id(17L).build())
        .build();
  }

  private Expense expense(String outlookMessageId) {
    return Expense.builder()
        .id(12L)
        .amount(new BigDecimal("40.00"))
        .description("Plumber")
        .date(LocalDate.of(2026, 8, 2))
        .category(ExpenseCategory.OTHER)
        .sourceType(ExpenseSource.OUTLOOK_EMAIL)
        .sourceId("message-002:attachment-1")
        .outlookMessageId(outlookMessageId)
        .activity(FinancialActivity.builder().id(4L).build())
        .financialCategory(FinancialCategory.builder().id(18L).build())
        .build();
  }
}
