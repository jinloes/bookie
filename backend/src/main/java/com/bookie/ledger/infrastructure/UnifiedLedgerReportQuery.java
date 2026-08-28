package com.bookie.ledger.infrastructure;

import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.ledger.application.LedgerReportData;
import com.bookie.ledger.application.LedgerReportParityException;
import com.bookie.ledger.application.LedgerReportQuery;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("unifiedLedgerReportQuery")
@RequiredArgsConstructor
class UnifiedLedgerReportQuery implements LedgerReportQuery {

  private static final Sort REPORT_ORDER = Sort.by(Sort.Order.asc("date"), Sort.Order.asc("id"));

  private final FinancialTransactionRepository transactionRepository;
  private final ReportPolicyResolver reportPolicyResolver;

  @Override
  @Transactional(readOnly = true)
  public LedgerReportData.CashflowTotals cashflow(LocalDate from, LocalDate to) {
    Map<Long, BigDecimal> incomeByActivity = new LinkedHashMap<>();
    Map<Long, BigDecimal> expensesByActivity = new LinkedHashMap<>();
    for (FinancialTransaction transaction : transactions(from, to)) {
      Map<Long, BigDecimal> destination =
          transaction.getDirection() == TransactionDirection.INCOME
              ? incomeByActivity
              : expensesByActivity;
      destination.merge(
          transaction.getActivity().getId(), transaction.getAmount(), BigDecimal::add);
    }
    return new LedgerReportData.CashflowTotals(
        activityTotals(incomeByActivity), activityTotals(expensesByActivity));
  }

  @Override
  @Transactional(readOnly = true)
  public LedgerReportData.ScheduleETotals scheduleE(LocalDate from, LocalDate to) {
    Map<Long, BigDecimal> incomeByActivity = new LinkedHashMap<>();
    Map<CategoryKey, BigDecimal> expensesByCategory = new LinkedHashMap<>();
    for (FinancialTransaction transaction : transactions(from, to)) {
      if (transaction.getDirection() == TransactionDirection.INCOME) {
        incomeByActivity.merge(
            transaction.getActivity().getId(), transaction.getAmount(), BigDecimal::add);
        continue;
      }
      ReportPolicyResolution resolution =
          reportPolicyResolver.resolveNeutral(
              transaction.getActivity().getId(),
              transaction.getNeutralCategory(),
              transaction.getDate());
      if (!(resolution instanceof ReportPolicyResolution.Resolved resolved)) {
        throw new LedgerReportParityException(
            "Unified ledger report category has no unique effective legacy mapping");
      }
      if (!transaction.getNeutralCategory().getId().equals(resolved.neutralCategory().getId())) {
        throw new LedgerReportParityException(
            "Unified ledger report category resolution returned a different neutral category");
      }
      CategoryKey key =
          new CategoryKey(
              transaction.getActivity().getId(),
              transaction.getNeutralCategory().getId(),
              resolved.legacyCategoryId());
      expensesByCategory.merge(key, transaction.getAmount(), BigDecimal::add);
    }
    return new LedgerReportData.ScheduleETotals(
        activityTotals(incomeByActivity), categoryTotals(expensesByCategory));
  }

  private List<FinancialTransaction> transactions(LocalDate from, LocalDate to) {
    return transactionRepository.findAllByDateBetweenAndDeletedAtIsNull(from, to, REPORT_ORDER);
  }

  private List<LedgerReportData.ActivityTotal> activityTotals(
      Map<Long, BigDecimal> totalsByActivity) {
    return totalsByActivity.entrySet().stream()
        .map(entry -> new LedgerReportData.ActivityTotal(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparing(LedgerReportData.ActivityTotal::activityId))
        .toList();
  }

  private List<LedgerReportData.ActivityCategoryTotal> categoryTotals(
      Map<CategoryKey, BigDecimal> totalsByCategory) {
    return totalsByCategory.entrySet().stream()
        .map(
            entry ->
                LedgerReportData.ActivityCategoryTotal.builder()
                    .activityId(entry.getKey().activityId())
                    .neutralCategoryId(entry.getKey().neutralCategoryId())
                    .legacyCategoryId(entry.getKey().legacyCategoryId())
                    .total(entry.getValue())
                    .build())
        .sorted(
            Comparator.comparing(LedgerReportData.ActivityCategoryTotal::activityId)
                .thenComparing(LedgerReportData.ActivityCategoryTotal::neutralCategoryId)
                .thenComparing(LedgerReportData.ActivityCategoryTotal::legacyCategoryId))
        .toList();
  }

  private record CategoryKey(Long activityId, Long neutralCategoryId, Long legacyCategoryId) {}
}
