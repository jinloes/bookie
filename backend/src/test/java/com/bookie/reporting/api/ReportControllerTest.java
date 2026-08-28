package com.bookie.reporting.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.model.TransactionDirection;
import com.bookie.reporting.application.ReportService;
import com.bookie.reporting.domain.ReportResults;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReportService reportService;

  @Nested
  class Cashflow {

    @Test
    void preservesTheExistingPayloadAndFilters() throws Exception {
      when(reportService.cashflow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 4L, 10L))
          .thenReturn(
              ReportResults.Cashflow.builder()
                  .from(LocalDate.of(2026, 1, 1))
                  .to(LocalDate.of(2026, 12, 31))
                  .totalIncome(new BigDecimal("3000.00"))
                  .totalExpenses(new BigDecimal("2700.00"))
                  .netCashflow(new BigDecimal("300.00"))
                  .activities(List.of())
                  .build());

      mockMvc
          .perform(
              get("/api/reports/cashflow?from=2026-01-01&to=2026-12-31&ownerId=4&activityId=10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalIncome").value(3000.0))
          .andExpect(jsonPath("$.totalExpenses").value(2700.0))
          .andExpect(jsonPath("$.netCashflow").value(300.0))
          .andExpect(jsonPath("$.activities").isArray());
    }
  }

  @Nested
  class ScheduleE {

    @Test
    void usesTheResolvedReportLineWithoutChangingThePayloadShape() throws Exception {
      HouseholdMember owner = HouseholdMember.builder().id(1L).name("Alex").active(true).build();
      FinancialActivity activity =
          FinancialActivity.builder()
              .id(10L)
              .name("Oak Street")
              .activityType(ActivityType.RENTAL)
              .taxTreatment(TaxTreatment.SCHEDULE_E)
              .owner(owner)
              .active(true)
              .build();
      LegacyCategoryView category =
          LegacyCategoryView.builder()
              .id(20L)
              .key("REPAIRS")
              .label("Repairs")
              .direction(TransactionDirection.EXPENSE)
              .taxTreatment(LegacyTaxTreatment.SCHEDULE_E)
              .taxLine("legacy-line")
              .active(true)
              .system(true)
              .build();
      ReportResults.CategoryTotal categoryTotal =
          ReportResults.CategoryTotal.builder()
              .category(category)
              .reportLine("14")
              .total(new BigDecimal("25.00"))
              .build();
      ReportResults.ScheduleEActivity activityTotal =
          ReportResults.ScheduleEActivity.builder()
              .activity(activity)
              .rentalIncome(new BigDecimal("100.00"))
              .expenses(new BigDecimal("25.00"))
              .netIncome(new BigDecimal("75.00"))
              .categories(List.of(categoryTotal))
              .build();
      when(reportService.scheduleE(2026, 1L, 10L))
          .thenReturn(
              ReportResults.ScheduleE.builder()
                  .year(2026)
                  .rentalIncome(new BigDecimal("100.00"))
                  .expenses(new BigDecimal("25.00"))
                  .netIncome(new BigDecimal("75.00"))
                  .activities(List.of(activityTotal))
                  .build());

      mockMvc
          .perform(get("/api/reports/schedule-e?year=2026&ownerId=1&activityId=10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.year").value(2026))
          .andExpect(jsonPath("$.activities[0].activity.name").value("Oak Street"))
          .andExpect(jsonPath("$.activities[0].categories[0].category.taxLine").value("14"))
          .andExpect(jsonPath("$.netIncome").value(75.0));
    }
  }
}
