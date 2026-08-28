package com.bookie.reporting.infrastructure;

import com.bookie.ledger.application.LedgerReportData;
import com.bookie.ledger.application.LedgerReportParityException;
import com.bookie.ledger.application.LedgerReportQuery;
import com.bookie.reporting.application.ReportingReadMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
class ComparingLedgerReportQuery implements LedgerReportQuery {

  private final LedgerReportQuery legacyQuery;
  private final LedgerReportQuery unifiedQuery;

  @Value("${bookie.reporting.read-mode:UNIFIED}")
  private ReportingReadMode readMode = ReportingReadMode.UNIFIED;

  ComparingLedgerReportQuery(
      @Qualifier("legacyLedgerReportQuery") LedgerReportQuery legacyQuery,
      @Qualifier("unifiedLedgerReportQuery") LedgerReportQuery unifiedQuery) {
    this.legacyQuery = legacyQuery;
    this.unifiedQuery = unifiedQuery;
  }

  @Override
  public LedgerReportData.CashflowTotals cashflow(LocalDate from, LocalDate to) {
    if (readMode == ReportingReadMode.LEGACY) {
      return legacyQuery.cashflow(from, to);
    }
    if (readMode == ReportingReadMode.UNIFIED) {
      return unifiedQuery.cashflow(from, to);
    }
    LedgerReportData.CashflowTotals legacy = legacyQuery.cashflow(from, to);
    LedgerReportData.CashflowTotals unified = unifiedQuery.cashflow(from, to);
    assertActivityTotalsEqual("cashflow income", legacy.incomeTotals(), unified.incomeTotals());
    assertActivityTotalsEqual("cashflow expenses", legacy.expenseTotals(), unified.expenseTotals());
    return legacy;
  }

  @Override
  public LedgerReportData.ScheduleETotals scheduleE(LocalDate from, LocalDate to) {
    if (readMode == ReportingReadMode.LEGACY) {
      return legacyQuery.scheduleE(from, to);
    }
    if (readMode == ReportingReadMode.UNIFIED) {
      return unifiedQuery.scheduleE(from, to);
    }
    LedgerReportData.ScheduleETotals legacy = legacyQuery.scheduleE(from, to);
    LedgerReportData.ScheduleETotals unified = unifiedQuery.scheduleE(from, to);
    assertActivityTotalsEqual("Schedule E income", legacy.incomeTotals(), unified.incomeTotals());
    assertCategoryTotalsEqual(legacy.expenseTotals(), unified.expenseTotals());
    return legacy;
  }

  private void assertActivityTotalsEqual(
      String description,
      List<LedgerReportData.ActivityTotal> legacy,
      List<LedgerReportData.ActivityTotal> unified) {
    assertNumericMapsEqual(description, activityTotals(legacy), activityTotals(unified));
  }

  private void assertCategoryTotalsEqual(
      List<LedgerReportData.ActivityCategoryTotal> legacy,
      List<LedgerReportData.ActivityCategoryTotal> unified) {
    assertNumericMapsEqual(
        "Schedule E category expenses", categoryTotals(legacy), categoryTotals(unified));
  }

  private Map<Long, BigDecimal> activityTotals(List<LedgerReportData.ActivityTotal> totals) {
    Map<Long, BigDecimal> normalized = new LinkedHashMap<>();
    totals.forEach(total -> normalized.merge(total.activityId(), total.total(), BigDecimal::add));
    return normalized;
  }

  private Map<CategoryKey, BigDecimal> categoryTotals(
      List<LedgerReportData.ActivityCategoryTotal> totals) {
    Map<CategoryKey, BigDecimal> normalized = new LinkedHashMap<>();
    totals.forEach(
        total ->
            normalized.merge(
                new CategoryKey(
                    total.activityId(), total.neutralCategoryId(), total.legacyCategoryId()),
                total.total(),
                BigDecimal::add));
    return normalized;
  }

  private <K> void assertNumericMapsEqual(
      String description, Map<K, BigDecimal> legacy, Map<K, BigDecimal> unified) {
    if (!legacy.keySet().equals(unified.keySet())) {
      throw new LedgerReportParityException(description + " grouping differs");
    }
    boolean amountDiffers =
        legacy.entrySet().stream()
            .anyMatch(entry -> entry.getValue().compareTo(unified.get(entry.getKey())) != 0);
    if (amountDiffers) {
      throw new LedgerReportParityException(description + " monetary totals differ");
    }
  }

  private record CategoryKey(Long activityId, Long neutralCategoryId, Long legacyCategoryId) {}
}
