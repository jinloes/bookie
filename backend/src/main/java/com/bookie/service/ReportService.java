package com.bookie.service;

import com.bookie.controller.ApiResponses;
import com.bookie.model.FinancialActivity;
import com.bookie.model.TaxTreatment;
import com.bookie.repository.ActivityCategoryTotalProjection;
import com.bookie.repository.ActivityTotalProjection;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final IncomeRepository incomeRepository;
  private final ExpenseRepository expenseRepository;
  private final FinancialActivityService financialActivityService;
  private final FinancialCategoryService financialCategoryService;

  public ApiResponses.CashflowSummaryResponse cashflow(LocalDate from, LocalDate to) {
    return cashflow(from, to, null, null);
  }

  public ApiResponses.CashflowSummaryResponse cashflow(
      LocalDate from, LocalDate to, Long ownerId, Long activityId) {
    validatePeriod(from, to);
    Map<Long, BigDecimal> incomeByActivity =
        totalsByActivity(incomeRepository.sumByActivityBetween(from, to));
    Map<Long, BigDecimal> expensesByActivity =
        totalsByActivity(expenseRepository.sumByActivityBetween(from, to));

    Map<Long, FinancialActivity> activities = activitiesById();
    List<ApiResponses.ActivityCashflowResponse> rows = new ArrayList<>();
    for (Long rowActivityId : unionKeys(incomeByActivity, expensesByActivity)) {
      FinancialActivity activity = activities.get(rowActivityId);
      if (activity == null) {
        throw new IllegalStateException("Report references missing activity: " + rowActivityId);
      }
      if (!matchesFilters(activity, ownerId, activityId)) {
        continue;
      }
      BigDecimal income = incomeByActivity.getOrDefault(rowActivityId, ZERO);
      BigDecimal expenses = expensesByActivity.getOrDefault(rowActivityId, ZERO);
      rows.add(
          new ApiResponses.ActivityCashflowResponse(
              ApiResponses.FinancialActivityResponse.from(activity),
              income,
              expenses,
              income.subtract(expenses)));
    }
    rows.sort(
        java.util.Comparator.comparing(
            row -> row.activity().name(), String.CASE_INSENSITIVE_ORDER));

    BigDecimal totalIncome =
        rows.stream()
            .map(ApiResponses.ActivityCashflowResponse::income)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal totalExpenses =
        rows.stream()
            .map(ApiResponses.ActivityCashflowResponse::expenses)
            .reduce(ZERO, BigDecimal::add);
    return new ApiResponses.CashflowSummaryResponse(
        from, to, totalIncome, totalExpenses, totalIncome.subtract(totalExpenses), rows);
  }

  public ApiResponses.ScheduleEReportResponse scheduleE(int year) {
    return scheduleE(year, null, null);
  }

  public ApiResponses.ScheduleEReportResponse scheduleE(int year, Long ownerId, Long activityId) {
    if (year < 1900 || year > 9999) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report year");
    }
    LocalDate from = LocalDate.of(year, 1, 1);
    LocalDate to = LocalDate.of(year, 12, 31);
    Map<Long, BigDecimal> incomeByActivity =
        totalsByActivity(incomeRepository.sumByActivityBetween(from, to));
    Map<Long, List<ActivityCategoryTotalProjection>> expensesByActivity = new HashMap<>();
    for (ActivityCategoryTotalProjection total :
        expenseRepository.sumScheduleEByActivityAndCategoryBetween(from, to)) {
      expensesByActivity
          .computeIfAbsent(total.getActivityId(), ignored -> new ArrayList<>())
          .add(total);
    }

    List<ApiResponses.ScheduleEActivityResponse> rows = new ArrayList<>();
    for (FinancialActivity activity : financialActivityService.findAll()) {
      if (activity.getTaxTreatment() != TaxTreatment.SCHEDULE_E
          || !matchesFilters(activity, ownerId, activityId)) {
        continue;
      }
      List<ApiResponses.CategoryTotalResponse> categoryTotals =
          expensesByActivity.getOrDefault(activity.getId(), List.of()).stream()
              .map(
                  total ->
                      new ApiResponses.CategoryTotalResponse(
                          ApiResponses.FinancialCategoryResponse.from(
                              financialCategoryService.findById(total.getCategoryId())),
                          total.getTotal()))
              .sorted(
                  java.util.Comparator.comparing(
                      total -> total.category().label(), String.CASE_INSENSITIVE_ORDER))
              .toList();
      BigDecimal expenses =
          categoryTotals.stream()
              .map(ApiResponses.CategoryTotalResponse::total)
              .reduce(ZERO, BigDecimal::add);
      BigDecimal income = incomeByActivity.getOrDefault(activity.getId(), ZERO);
      rows.add(
          new ApiResponses.ScheduleEActivityResponse(
              ApiResponses.FinancialActivityResponse.from(activity),
              income,
              expenses,
              income.subtract(expenses),
              categoryTotals));
    }
    rows.sort(
        java.util.Comparator.comparing(
            row -> row.activity().name(), String.CASE_INSENSITIVE_ORDER));
    BigDecimal totalIncome =
        rows.stream()
            .map(ApiResponses.ScheduleEActivityResponse::rentalIncome)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal totalExpenses =
        rows.stream()
            .map(ApiResponses.ScheduleEActivityResponse::expenses)
            .reduce(ZERO, BigDecimal::add);
    return new ApiResponses.ScheduleEReportResponse(
        year, totalIncome, totalExpenses, totalIncome.subtract(totalExpenses), rows);
  }

  private Map<Long, FinancialActivity> activitiesById() {
    Map<Long, FinancialActivity> activities = new LinkedHashMap<>();
    financialActivityService
        .findAll()
        .forEach(activity -> activities.put(activity.getId(), activity));
    return activities;
  }

  private Map<Long, BigDecimal> totalsByActivity(List<ActivityTotalProjection> totals) {
    Map<Long, BigDecimal> byActivity = new HashMap<>();
    totals.forEach(total -> byActivity.put(total.getActivityId(), total.getTotal()));
    return byActivity;
  }

  private java.util.Set<Long> unionKeys(
      Map<Long, BigDecimal> incomeByActivity, Map<Long, BigDecimal> expensesByActivity) {
    java.util.Set<Long> keys = new java.util.LinkedHashSet<>(incomeByActivity.keySet());
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
}
