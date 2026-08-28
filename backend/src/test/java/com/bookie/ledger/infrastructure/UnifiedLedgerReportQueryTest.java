package com.bookie.ledger.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import com.bookie.ledger.application.LedgerReportData;
import com.bookie.ledger.application.LedgerReportParityException;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class UnifiedLedgerReportQueryTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 12, 31);

  @Mock private FinancialTransactionRepository transactionRepository;
  @Mock private ReportPolicyResolver reportPolicyResolver;

  private UnifiedLedgerReportQuery query;
  private FinancialActivity activity;
  private NeutralCategory incomeCategory;
  private NeutralCategory expenseCategory;

  @BeforeEach
  void setUp() {
    query = new UnifiedLedgerReportQuery(transactionRepository, reportPolicyResolver);
    activity = FinancialActivity.builder().id(10L).build();
    incomeCategory =
        NeutralCategory.builder()
            .id(20L)
            .direction(TransactionDirection.INCOME)
            .active(true)
            .build();
    expenseCategory =
        NeutralCategory.builder()
            .id(21L)
            .direction(TransactionDirection.EXPENSE)
            .active(true)
            .build();
  }

  @Nested
  class Cashflow {

    @Test
    void aggregatesUnifiedAmountsByDirectionAndActivity() {
      when(transactionRepository.findAllByDateBetweenAndDeletedAtIsNull(
              eq(FROM), eq(TO), any(Sort.class)))
          .thenReturn(
              List.of(
                  transaction(1L, TransactionDirection.INCOME, incomeCategory, "10.10"),
                  transaction(2L, TransactionDirection.INCOME, incomeCategory, "2.20"),
                  transaction(3L, TransactionDirection.EXPENSE, expenseCategory, "4.05")));

      LedgerReportData.CashflowTotals result = query.cashflow(FROM, TO);

      assertThat(result.incomeTotals())
          .containsExactly(new LedgerReportData.ActivityTotal(10L, new BigDecimal("12.30")));
      assertThat(result.expenseTotals())
          .containsExactly(new LedgerReportData.ActivityTotal(10L, new BigDecimal("4.05")));
    }
  }

  @Nested
  class ScheduleE {

    @Test
    void resolvesAndAggregatesEffectiveLegacyCategoryMappings() {
      FinancialTransaction income =
          transaction(1L, TransactionDirection.INCOME, incomeCategory, "100.00");
      FinancialTransaction firstExpense =
          transaction(2L, TransactionDirection.EXPENSE, expenseCategory, "20.00");
      FinancialTransaction secondExpense =
          transaction(3L, TransactionDirection.EXPENSE, expenseCategory, "5.25");
      when(transactionRepository.findAllByDateBetweenAndDeletedAtIsNull(
              eq(FROM), eq(TO), any(Sort.class)))
          .thenReturn(List.of(income, firstExpense, secondExpense));
      ReportPolicyResolution.Resolved resolution = resolved(expenseCategory);
      when(reportPolicyResolver.resolveNeutral(
              activity.getId(), expenseCategory, firstExpense.getDate()))
          .thenReturn(resolution);
      when(reportPolicyResolver.resolveNeutral(
              activity.getId(), expenseCategory, secondExpense.getDate()))
          .thenReturn(resolution);

      LedgerReportData.ScheduleETotals result = query.scheduleE(FROM, TO);

      assertThat(result.incomeTotals())
          .containsExactly(new LedgerReportData.ActivityTotal(10L, new BigDecimal("100.00")));
      assertThat(result.expenseTotals())
          .containsExactly(
              LedgerReportData.ActivityCategoryTotal.builder()
                  .activityId(10L)
                  .neutralCategoryId(21L)
                  .legacyCategoryId(30L)
                  .total(new BigDecimal("25.25"))
                  .build());
    }

    @Test
    void failsClosedWhenTheEffectiveMappingIsMissing() {
      FinancialTransaction expense =
          transaction(2L, TransactionDirection.EXPENSE, expenseCategory, "20.00");
      when(transactionRepository.findAllByDateBetweenAndDeletedAtIsNull(
              eq(FROM), eq(TO), any(Sort.class)))
          .thenReturn(List.of(expense));
      when(reportPolicyResolver.resolveNeutral(
              activity.getId(), expenseCategory, expense.getDate()))
          .thenReturn(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING));

      assertThatThrownBy(() -> query.scheduleE(FROM, TO))
          .isInstanceOf(LedgerReportParityException.class)
          .hasMessageContaining("no unique effective legacy mapping");
    }

    @Test
    void failsClosedWhenResolutionReturnsADifferentNeutralCategory() {
      FinancialTransaction expense =
          transaction(2L, TransactionDirection.EXPENSE, expenseCategory, "20.00");
      NeutralCategory different = NeutralCategory.builder().id(99L).build();
      when(transactionRepository.findAllByDateBetweenAndDeletedAtIsNull(
              eq(FROM), eq(TO), any(Sort.class)))
          .thenReturn(List.of(expense));
      when(reportPolicyResolver.resolveNeutral(
              activity.getId(), expenseCategory, expense.getDate()))
          .thenReturn(resolved(different));

      assertThatThrownBy(() -> query.scheduleE(FROM, TO))
          .isInstanceOf(LedgerReportParityException.class)
          .hasMessageContaining("different neutral category");
    }
  }

  private FinancialTransaction transaction(
      Long id, TransactionDirection direction, NeutralCategory neutralCategory, String amount) {
    return FinancialTransaction.builder()
        .id(id)
        .activity(activity)
        .direction(direction)
        .neutralCategory(neutralCategory)
        .date(LocalDate.of(2026, 6, Math.toIntExact(id)))
        .amount(new BigDecimal(amount))
        .build();
  }

  private ReportPolicyResolution.Resolved resolved(NeutralCategory neutralCategory) {
    ReportingProfile profile =
        ReportingProfile.builder().id(40L).key(ReportingProfileKey.SCHEDULE_E).active(true).build();
    return new ReportPolicyResolution.Resolved(neutralCategory, profile, 30L, "14", true);
  }
}
