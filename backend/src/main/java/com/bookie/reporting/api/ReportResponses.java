package com.bookie.reporting.api;

import com.bookie.catalog.activity.api.FinancialActivityResponse;
import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.reporting.domain.ReportResults;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

public final class ReportResponses {

  private ReportResponses() {}

  @Builder
  public record ActivityCashflowResponse(
      FinancialActivityResponse activity,
      BigDecimal income,
      BigDecimal expenses,
      BigDecimal netCashflow) {

    private static ActivityCashflowResponse from(ReportResults.ActivityCashflow result) {
      return ActivityCashflowResponse.builder()
          .activity(FinancialActivityResponse.from(result.activity()))
          .income(result.income())
          .expenses(result.expenses())
          .netCashflow(result.netCashflow())
          .build();
    }
  }

  @Builder
  public record CashflowSummaryResponse(
      LocalDate from,
      LocalDate to,
      BigDecimal totalIncome,
      BigDecimal totalExpenses,
      BigDecimal netCashflow,
      List<ActivityCashflowResponse> activities) {

    public CashflowSummaryResponse {
      activities = List.copyOf(activities);
    }

    public static CashflowSummaryResponse from(ReportResults.Cashflow result) {
      return CashflowSummaryResponse.builder()
          .from(result.from())
          .to(result.to())
          .totalIncome(result.totalIncome())
          .totalExpenses(result.totalExpenses())
          .netCashflow(result.netCashflow())
          .activities(result.activities().stream().map(ActivityCashflowResponse::from).toList())
          .build();
    }
  }

  public record CategoryTotalResponse(FinancialCategoryResponse category, BigDecimal total) {

    private static CategoryTotalResponse from(ReportResults.CategoryTotal result) {
      FinancialCategoryResponse category = FinancialCategoryResponse.from(result.category());
      FinancialCategoryResponse response =
          new FinancialCategoryResponse(
              category.id(),
              category.key(),
              category.label(),
              category.direction(),
              category.taxTreatment(),
              result.reportLine(),
              category.active(),
              category.system());
      return new CategoryTotalResponse(response, result.total());
    }
  }

  @Builder
  public record ScheduleEActivityResponse(
      FinancialActivityResponse activity,
      BigDecimal rentalIncome,
      BigDecimal expenses,
      BigDecimal netIncome,
      List<CategoryTotalResponse> categories) {

    public ScheduleEActivityResponse {
      categories = List.copyOf(categories);
    }

    private static ScheduleEActivityResponse from(ReportResults.ScheduleEActivity result) {
      return ScheduleEActivityResponse.builder()
          .activity(FinancialActivityResponse.from(result.activity()))
          .rentalIncome(result.rentalIncome())
          .expenses(result.expenses())
          .netIncome(result.netIncome())
          .categories(result.categories().stream().map(CategoryTotalResponse::from).toList())
          .build();
    }
  }

  @Builder
  public record ScheduleEReportResponse(
      int year,
      BigDecimal rentalIncome,
      BigDecimal expenses,
      BigDecimal netIncome,
      List<ScheduleEActivityResponse> activities) {

    public ScheduleEReportResponse {
      activities = List.copyOf(activities);
    }

    public static ScheduleEReportResponse from(ReportResults.ScheduleE result) {
      return ScheduleEReportResponse.builder()
          .year(result.year())
          .rentalIncome(result.rentalIncome())
          .expenses(result.expenses())
          .netIncome(result.netIncome())
          .activities(result.activities().stream().map(ScheduleEActivityResponse::from).toList())
          .build();
    }
  }
}
