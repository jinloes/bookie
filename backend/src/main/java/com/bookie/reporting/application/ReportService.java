package com.bookie.reporting.application;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportPolicyReadMode;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.catalog.reportpolicy.application.ReportingProfilePeriodResolution;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import com.bookie.ledger.application.LedgerReportData;
import com.bookie.ledger.application.LedgerReportQuery;
import com.bookie.model.TransactionDirection;
import com.bookie.reporting.domain.ReportResults;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final LedgerReportQuery ledgerReportQuery;
  private final ActivityCatalog activityCatalog;
  private final CategoryCatalog categoryCatalog;
  private final ReportPolicyResolver reportPolicyResolver;

  @Value("${bookie.report-policy.mode:NEW}")
  private ReportPolicyReadMode reportPolicyReadMode = ReportPolicyReadMode.NEW;

  public ReportResults.Cashflow cashflow(LocalDate from, LocalDate to) {
    return cashflow(from, to, null, null);
  }

  public ReportResults.Cashflow cashflow(
      LocalDate from, LocalDate to, Long ownerId, Long activityId) {
    validatePeriod(from, to);
    LedgerReportData.CashflowTotals totals = ledgerReportQuery.cashflow(from, to);
    Map<Long, BigDecimal> incomeByActivity = totalsByActivity(totals.incomeTotals());
    Map<Long, BigDecimal> expensesByActivity = totalsByActivity(totals.expenseTotals());

    Map<Long, FinancialActivity> activities = activitiesById();
    List<ReportResults.ActivityCashflow> rows = new ArrayList<>();
    for (Long rowActivityId : unionKeys(incomeByActivity, expensesByActivity)) {
      FinancialActivity activity = requiredActivity(activities, rowActivityId);
      if (!matchesFilters(activity, ownerId, activityId)) {
        continue;
      }
      BigDecimal income = incomeByActivity.getOrDefault(rowActivityId, ZERO);
      BigDecimal expenses = expensesByActivity.getOrDefault(rowActivityId, ZERO);
      rows.add(
          ReportResults.ActivityCashflow.builder()
              .activity(activity)
              .income(income)
              .expenses(expenses)
              .netCashflow(income.subtract(expenses))
              .build());
    }
    rows.sort(
        java.util.Comparator.comparing(
            row -> row.activity().getName(), String.CASE_INSENSITIVE_ORDER));

    BigDecimal totalIncome =
        rows.stream().map(ReportResults.ActivityCashflow::income).reduce(ZERO, BigDecimal::add);
    BigDecimal totalExpenses =
        rows.stream().map(ReportResults.ActivityCashflow::expenses).reduce(ZERO, BigDecimal::add);
    return ReportResults.Cashflow.builder()
        .from(from)
        .to(to)
        .totalIncome(totalIncome)
        .totalExpenses(totalExpenses)
        .netCashflow(totalIncome.subtract(totalExpenses))
        .activities(rows)
        .build();
  }

  public ReportResults.ScheduleE scheduleE(int year) {
    return scheduleE(year, null, null);
  }

  public ReportResults.ScheduleE scheduleE(int year, Long ownerId, Long activityId) {
    if (year < 1900 || year > 9999) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report year");
    }
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);
    LedgerReportData.ScheduleETotals totals = ledgerReportQuery.scheduleE(from, to);
    Map<Long, BigDecimal> incomeByActivity = totalsByActivity(totals.incomeTotals());
    Map<Long, List<LedgerReportData.ActivityCategoryTotal>> expensesByActivity = new HashMap<>();
    totals
        .expenseTotals()
        .forEach(
            total ->
                expensesByActivity
                    .computeIfAbsent(total.activityId(), ignored -> new ArrayList<>())
                    .add(total));

    List<ReportResults.ScheduleEActivity> rows = new ArrayList<>();
    for (FinancialActivity activity : activityCatalog.findAll()) {
      if (!matchesFilters(activity, ownerId, activityId)
          || !usesScheduleEProfile(activity, from, to)) {
        continue;
      }
      List<ReportResults.CategoryTotal> categoryTotals =
          expensesByActivity.getOrDefault(activity.getId(), List.of()).stream()
              .map(total -> scheduleECategory(activity, total, from, to))
              .flatMap(Optional::stream)
              .sorted(
                  java.util.Comparator.comparing(
                      total -> total.category().label(), String.CASE_INSENSITIVE_ORDER))
              .toList();
      BigDecimal expenses =
          categoryTotals.stream()
              .map(ReportResults.CategoryTotal::total)
              .reduce(ZERO, BigDecimal::add);
      BigDecimal income = incomeByActivity.getOrDefault(activity.getId(), ZERO);
      rows.add(
          ReportResults.ScheduleEActivity.builder()
              .activity(activity)
              .rentalIncome(income)
              .expenses(expenses)
              .netIncome(income.subtract(expenses))
              .categories(categoryTotals)
              .build());
    }
    rows.sort(
        java.util.Comparator.comparing(
            row -> row.activity().getName(), String.CASE_INSENSITIVE_ORDER));
    BigDecimal totalIncome =
        rows.stream()
            .map(ReportResults.ScheduleEActivity::rentalIncome)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal totalExpenses =
        rows.stream().map(ReportResults.ScheduleEActivity::expenses).reduce(ZERO, BigDecimal::add);
    return ReportResults.ScheduleE.builder()
        .year(year)
        .rentalIncome(totalIncome)
        .expenses(totalExpenses)
        .netIncome(totalIncome.subtract(totalExpenses))
        .activities(rows)
        .build();
  }

  private Optional<ReportResults.CategoryTotal> scheduleECategory(
      FinancialActivity activity,
      LedgerReportData.ActivityCategoryTotal total,
      LocalDate from,
      LocalDate to) {
    LegacyCategoryView category =
        categoryCatalog
            .findLegacyCategoryById(total.legacyCategoryId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Report references missing category: " + total.legacyCategoryId()));
    boolean legacyIncludes = category.taxTreatment() == LegacyTaxTreatment.SCHEDULE_E;
    if (reportPolicyReadMode == ReportPolicyReadMode.LEGACY) {
      return legacyIncludes
          ? Optional.of(categoryTotal(category, category.taxLine(), total.total()))
          : Optional.empty();
    }

    PolicyCategory start = resolvePolicyCategory(activity, total, from);
    PolicyCategory end = resolvePolicyCategory(activity, total, to);
    if (!start.sameAs(end)) {
      throw parityFailure("Report category mapping changes within the report period");
    }
    if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
      if (legacyIncludes != start.included()) {
        throw parityFailure("Report category inclusion differs from legacy behavior");
      }
      if (start.resolved() != null) {
        assertPolicyMatchesLegacy(category, activity, total.neutralCategoryId(), start.resolved());
      }
      return legacyIncludes
          ? Optional.of(categoryTotal(category, category.taxLine(), total.total()))
          : Optional.empty();
    }
    return start.included()
        ? Optional.of(categoryTotal(category, start.reportLine(), total.total()))
        : Optional.empty();
  }

  private ReportResults.CategoryTotal categoryTotal(
      LegacyCategoryView category, String reportLine, BigDecimal total) {
    return ReportResults.CategoryTotal.builder()
        .category(category)
        .reportLine(reportLine)
        .total(total)
        .build();
  }

  private PolicyCategory resolvePolicyCategory(
      FinancialActivity activity,
      LedgerReportData.ActivityCategoryTotal total,
      LocalDate effectiveOn) {
    ReportPolicyResolution resolution =
        reportPolicyResolver.resolve(activity.getId(), total.legacyCategoryId(), effectiveOn);
    if (resolution instanceof ReportPolicyResolution.Incompatible
        || (resolution instanceof ReportPolicyResolution.Missing missing
            && missing.component()
                == ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING)) {
      return new PolicyCategory(false, null, null);
    }
    if (!(resolution instanceof ReportPolicyResolution.Resolved resolved)) {
      throw parityFailure("Report category mapping is missing or ambiguous");
    }
    if (!total.neutralCategoryId().equals(resolved.neutralCategory().getId())) {
      throw parityFailure("Ledger and report-policy neutral categories differ");
    }
    boolean included =
        resolved.reportingProfile().isActive()
            && resolved.reportingProfile().getKey() == ReportingProfileKey.SCHEDULE_E
            && resolved.neutralCategory().isActive()
            && resolved.neutralCategory().getDirection() == TransactionDirection.EXPENSE
            && resolved.mappingActive();
    return new PolicyCategory(included, resolved.reportLine(), resolved);
  }

  private boolean usesScheduleEProfile(
      FinancialActivity activity, LocalDate effectiveFrom, LocalDate effectiveTo) {
    boolean legacyMatches = activity.getTaxTreatment() == TaxTreatment.SCHEDULE_E;
    if (reportPolicyReadMode == ReportPolicyReadMode.LEGACY) {
      return legacyMatches;
    }
    ReportingProfilePeriodResolution resolution =
        reportPolicyResolver.resolveProfileForPeriod(activity.getId(), effectiveFrom, effectiveTo);
    if (!(resolution instanceof ReportingProfilePeriodResolution.Resolved resolved)
        || !resolved.reportingProfile().isActive()) {
      throw parityFailure("Activity reporting profile period is missing or ambiguous");
    }
    boolean policyMatches = resolved.reportingProfile().getKey() == ReportingProfileKey.SCHEDULE_E;
    if (reportPolicyReadMode == ReportPolicyReadMode.COMPARE) {
      ReportingProfileKey legacyProfile =
          ReportingProfileKey.valueOf(activity.getTaxTreatment().name());
      if (resolved.reportingProfile().getKey() != legacyProfile) {
        throw parityFailure("Activity reporting profile differs from its legacy treatment");
      }
      return legacyMatches;
    }
    return policyMatches;
  }

  private void assertPolicyMatchesLegacy(
      LegacyCategoryView category,
      FinancialActivity activity,
      Long neutralCategoryId,
      ReportPolicyResolution.Resolved resolved) {
    if (!category.id().equals(resolved.legacyCategoryId())
        || !neutralCategoryId.equals(resolved.neutralCategory().getId())
        || resolved.reportingProfile().getKey()
            != ReportingProfileKey.valueOf(activity.getTaxTreatment().name())
        || resolved.neutralCategory().getDirection() != category.direction()
        || !Objects.equals(resolved.reportLine(), category.taxLine())
        || resolved.mappingActive() != category.active()) {
      throw parityFailure("Resolved report mapping differs from legacy category output");
    }
  }

  private Map<Long, FinancialActivity> activitiesById() {
    Map<Long, FinancialActivity> activities = new LinkedHashMap<>();
    activityCatalog.findAll().forEach(activity -> activities.put(activity.getId(), activity));
    return activities;
  }

  private FinancialActivity requiredActivity(
      Map<Long, FinancialActivity> activities, Long activityId) {
    FinancialActivity activity = activities.get(activityId);
    if (activity == null) {
      throw new IllegalStateException("Report references missing activity: " + activityId);
    }
    return activity;
  }

  private Map<Long, BigDecimal> totalsByActivity(List<LedgerReportData.ActivityTotal> totals) {
    Map<Long, BigDecimal> byActivity = new HashMap<>();
    totals.forEach(total -> byActivity.merge(total.activityId(), total.total(), BigDecimal::add));
    return byActivity;
  }

  private Set<Long> unionKeys(
      Map<Long, BigDecimal> incomeByActivity, Map<Long, BigDecimal> expensesByActivity) {
    Set<Long> keys = new LinkedHashSet<>(incomeByActivity.keySet());
    keys.addAll(expensesByActivity.keySet());
    return keys;
  }

  private void validatePeriod(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Report start date must be on or before end date");
    }
  }

  private boolean matchesFilters(
      FinancialActivity activity, Long ownerId, Long selectedActivityId) {
    if (selectedActivityId != null && !selectedActivityId.equals(activity.getId())) {
      return false;
    }
    return ownerId == null || ownerId.equals(activity.getOwner().getId());
  }

  private ReportPolicyParityException parityFailure(String message) {
    return new ReportPolicyParityException(message);
  }

  private record PolicyCategory(
      boolean included, String reportLine, ReportPolicyResolution.Resolved resolved) {

    private boolean sameAs(PolicyCategory other) {
      if (included != other.included || !Objects.equals(reportLine, other.reportLine)) {
        return false;
      }
      if (resolved == null || other.resolved == null) {
        return resolved == other.resolved;
      }
      return Objects.equals(
              resolved.neutralCategory().getId(), other.resolved.neutralCategory().getId())
          && resolved.reportingProfile().getKey() == other.resolved.reportingProfile().getKey()
          && Objects.equals(resolved.legacyCategoryId(), other.resolved.legacyCategoryId())
          && resolved.mappingActive() == other.resolved.mappingActive();
    }
  }
}
