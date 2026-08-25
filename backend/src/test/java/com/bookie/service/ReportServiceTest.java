package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bookie.controller.ApiResponses;
import com.bookie.model.ActivityType;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.HouseholdMember;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.ActivityCategoryTotalProjection;
import com.bookie.repository.ActivityTotalProjection;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  @Mock private IncomeRepository incomeRepository;
  @Mock private ExpenseRepository expenseRepository;
  @Mock private FinancialActivityService financialActivityService;
  @Mock private FinancialCategoryService financialCategoryService;

  @InjectMocks private ReportService reportService;

  private FinancialActivity teaching;
  private FinancialActivity tutoring;

  @BeforeEach
  void setUp() {
    HouseholdMember owner = HouseholdMember.builder().id(1L).name("Alex").active(true).build();
    teaching =
        FinancialActivity.builder()
            .id(10L)
            .name("Teaching")
            .activityType(ActivityType.EMPLOYMENT)
            .taxTreatment(TaxTreatment.W2)
            .owner(owner)
            .active(true)
            .build();
    tutoring =
        FinancialActivity.builder()
            .id(11L)
            .name("Tutoring")
            .activityType(ActivityType.SELF_EMPLOYMENT)
            .taxTreatment(TaxTreatment.SCHEDULE_C)
            .owner(owner)
            .active(true)
            .build();
  }

  @Test
  void cashflowCombinesIncomeAndExpensesByActivityWithoutClampingNet() {
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 12, 31);
    when(incomeRepository.sumByActivityBetween(from, to))
        .thenReturn(List.of(total(10L, "2400.00"), total(11L, "600.00")));
    when(expenseRepository.sumByActivityBetween(from, to))
        .thenReturn(List.of(total(10L, "2600.00"), total(11L, "100.00")));
    when(financialActivityService.findAll()).thenReturn(List.of(teaching, tutoring));

    ApiResponses.CashflowSummaryResponse report = reportService.cashflow(from, to);

    assertThat(report.totalIncome()).isEqualByComparingTo("3000.00");
    assertThat(report.totalExpenses()).isEqualByComparingTo("2700.00");
    assertThat(report.netCashflow()).isEqualByComparingTo("300.00");
    assertThat(report.activities())
        .filteredOn(row -> row.activity().id().equals(10L))
        .singleElement()
        .extracting(ApiResponses.ActivityCashflowResponse::netCashflow)
        .isEqualTo(new BigDecimal("-200.00"));
  }

  @Test
  void cashflowFiltersTotalsByOwnerAndActivity() {
    HouseholdMember otherOwner =
        HouseholdMember.builder().id(2L).name("Jordan").active(true).build();
    tutoring.setOwner(otherOwner);
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 12, 31);
    when(incomeRepository.sumByActivityBetween(from, to))
        .thenReturn(List.of(total(10L, "2400.00"), total(11L, "600.00")));
    when(expenseRepository.sumByActivityBetween(from, to))
        .thenReturn(List.of(total(10L, "200.00"), total(11L, "100.00")));
    when(financialActivityService.findAll()).thenReturn(List.of(teaching, tutoring));

    ApiResponses.CashflowSummaryResponse report =
        reportService.cashflow(from, to, 1L, teaching.getId());

    assertThat(report.totalIncome()).isEqualByComparingTo("2400.00");
    assertThat(report.totalExpenses()).isEqualByComparingTo("200.00");
    assertThat(report.activities())
        .singleElement()
        .satisfies(row -> assertThat(row.activity().id()).isEqualTo(teaching.getId()));
  }

  @Test
  void scheduleEGroupsRentalExpensesByCatalogCategory() {
    FinancialActivity rental =
        FinancialActivity.builder()
            .id(12L)
            .name("Oak Street")
            .activityType(ActivityType.RENTAL)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .owner(teaching.getOwner())
            .active(true)
            .build();
    FinancialCategory repairs =
        FinancialCategory.builder()
            .id(20L)
            .key("REPAIRS")
            .label("Repairs")
            .direction(TransactionDirection.EXPENSE)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .taxLine("14")
            .active(true)
            .build();
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 12, 31);
    when(incomeRepository.sumByActivityBetween(from, to))
        .thenReturn(List.of(total(12L, "1000.00")));
    when(expenseRepository.sumScheduleEByActivityAndCategoryBetween(from, to))
        .thenReturn(List.of(categoryTotal(12L, 20L, "1200.00")));
    when(financialActivityService.findAll()).thenReturn(List.of(rental, teaching));
    when(financialCategoryService.findById(20L)).thenReturn(repairs);

    ApiResponses.ScheduleEReportResponse report = reportService.scheduleE(2026);

    assertThat(report.rentalIncome()).isEqualByComparingTo("1000.00");
    assertThat(report.expenses()).isEqualByComparingTo("1200.00");
    assertThat(report.netIncome()).isEqualByComparingTo("-200.00");
    assertThat(report.activities())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.activity().name()).isEqualTo("Oak Street");
              assertThat(row.categories())
                  .singleElement()
                  .satisfies(category -> assertThat(category.category().taxLine()).isEqualTo("14"));
            });
  }

  @Test
  void scheduleEFiltersTotalsByOwnerAndActivity() {
    FinancialActivity oakRental =
        FinancialActivity.builder()
            .id(12L)
            .name("Oak Street")
            .activityType(ActivityType.RENTAL)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .owner(teaching.getOwner())
            .active(true)
            .build();
    HouseholdMember otherOwner =
        HouseholdMember.builder().id(2L).name("Jordan").active(true).build();
    FinancialActivity pineRental =
        FinancialActivity.builder()
            .id(13L)
            .name("Pine Street")
            .activityType(ActivityType.RENTAL)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .owner(otherOwner)
            .active(true)
            .build();
    FinancialCategory repairs =
        FinancialCategory.builder()
            .id(20L)
            .key("REPAIRS")
            .label("Repairs")
            .direction(TransactionDirection.EXPENSE)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .active(true)
            .build();
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 12, 31);
    when(incomeRepository.sumByActivityBetween(from, to))
        .thenReturn(List.of(total(12L, "1000.00"), total(13L, "500.00")));
    when(expenseRepository.sumScheduleEByActivityAndCategoryBetween(from, to))
        .thenReturn(List.of(categoryTotal(12L, 20L, "200.00"), categoryTotal(13L, 20L, "50.00")));
    when(financialActivityService.findAll()).thenReturn(List.of(oakRental, pineRental));
    when(financialCategoryService.findById(20L)).thenReturn(repairs);

    ApiResponses.ScheduleEReportResponse report =
        reportService.scheduleE(2026, teaching.getOwner().getId(), oakRental.getId());

    assertThat(report.rentalIncome()).isEqualByComparingTo("1000.00");
    assertThat(report.expenses()).isEqualByComparingTo("200.00");
    assertThat(report.netIncome()).isEqualByComparingTo("800.00");
    assertThat(report.activities())
        .singleElement()
        .satisfies(row -> assertThat(row.activity().id()).isEqualTo(oakRental.getId()));
  }

  private ActivityTotalProjection total(Long activityId, String value) {
    return new ActivityTotalProjection() {
      @Override
      public Long getActivityId() {
        return activityId;
      }

      @Override
      public BigDecimal getTotal() {
        return new BigDecimal(value);
      }
    };
  }

  private ActivityCategoryTotalProjection categoryTotal(
      Long activityId, Long categoryId, String value) {
    return new ActivityCategoryTotalProjection() {
      @Override
      public Long getActivityId() {
        return activityId;
      }

      @Override
      public Long getCategoryId() {
        return categoryId;
      }

      @Override
      public BigDecimal getTotal() {
        return new BigDecimal(value);
      }
    };
  }
}
