package com.bookie.reporting.domain;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.application.LegacyCategoryView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

public final class ReportResults {

  private ReportResults() {}

  @Builder
  public record ActivityCashflow(
      FinancialActivity activity, BigDecimal income, BigDecimal expenses, BigDecimal netCashflow) {}

  @Builder
  public record Cashflow(
      LocalDate from,
      LocalDate to,
      BigDecimal totalIncome,
      BigDecimal totalExpenses,
      BigDecimal netCashflow,
      List<ActivityCashflow> activities) {

    public Cashflow {
      activities = List.copyOf(activities);
    }
  }

  @Builder
  public record CategoryTotal(LegacyCategoryView category, String reportLine, BigDecimal total) {}

  @Builder
  public record ScheduleEActivity(
      FinancialActivity activity,
      BigDecimal rentalIncome,
      BigDecimal expenses,
      BigDecimal netIncome,
      List<CategoryTotal> categories) {

    public ScheduleEActivity {
      categories = List.copyOf(categories);
    }
  }

  @Builder
  public record ScheduleE(
      int year,
      BigDecimal rentalIncome,
      BigDecimal expenses,
      BigDecimal netIncome,
      List<ScheduleEActivity> activities) {

    public ScheduleE {
      activities = List.copyOf(activities);
    }
  }
}
