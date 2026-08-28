package com.bookie.ledger.compatibility;

import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.ledger.application.LedgerReportData;
import com.bookie.ledger.application.LedgerReportParityException;
import com.bookie.ledger.application.LedgerReportQuery;
import com.bookie.repository.ActivityCategoryTotalProjection;
import com.bookie.repository.ActivityTotalProjection;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("legacyLedgerReportQuery")
@RequiredArgsConstructor
class LegacyLedgerReportQuery implements LedgerReportQuery {

  private static final Comparator<LedgerReportData.ActivityTotal> ACTIVITY_ORDER =
      Comparator.comparing(LedgerReportData.ActivityTotal::activityId);
  private static final Comparator<LedgerReportData.ActivityCategoryTotal> CATEGORY_ORDER =
      Comparator.comparing(LedgerReportData.ActivityCategoryTotal::activityId)
          .thenComparing(LedgerReportData.ActivityCategoryTotal::neutralCategoryId)
          .thenComparing(LedgerReportData.ActivityCategoryTotal::legacyCategoryId);

  private final IncomeRepository incomeRepository;
  private final ExpenseRepository expenseRepository;
  private final CategoryCatalog categoryCatalog;

  @Override
  @Transactional(readOnly = true)
  public LedgerReportData.CashflowTotals cashflow(
      java.time.LocalDate from, java.time.LocalDate to) {
    return new LedgerReportData.CashflowTotals(
        activityTotals(incomeRepository.sumByActivityBetween(from, to)),
        activityTotals(expenseRepository.sumByActivityBetween(from, to)));
  }

  @Override
  @Transactional(readOnly = true)
  public LedgerReportData.ScheduleETotals scheduleE(
      java.time.LocalDate from, java.time.LocalDate to) {
    List<LedgerReportData.ActivityCategoryTotal> expenses =
        expenseRepository.sumByActivityAndCategoryBetween(from, to).stream()
            .map(this::categoryTotal)
            .sorted(CATEGORY_ORDER)
            .toList();
    return new LedgerReportData.ScheduleETotals(
        activityTotals(incomeRepository.sumByActivityBetween(from, to)), expenses);
  }

  private List<LedgerReportData.ActivityTotal> activityTotals(
      List<ActivityTotalProjection> projections) {
    return projections.stream()
        .map(
            projection ->
                new LedgerReportData.ActivityTotal(
                    projection.getActivityId(), projection.getTotal()))
        .sorted(ACTIVITY_ORDER)
        .toList();
  }

  private LedgerReportData.ActivityCategoryTotal categoryTotal(
      ActivityCategoryTotalProjection projection) {
    LegacyCategoryMap categoryMap =
        categoryCatalog
            .findByLegacyCategoryId(projection.getCategoryId())
            .orElseThrow(
                () ->
                    new LedgerReportParityException(
                        "Legacy report category is missing its neutral mapping: "
                            + projection.getCategoryId()));
    return LedgerReportData.ActivityCategoryTotal.builder()
        .activityId(projection.getActivityId())
        .neutralCategoryId(categoryMap.getNeutralCategory().getId())
        .legacyCategoryId(projection.getCategoryId())
        .total(projection.getTotal())
        .build();
  }
}
