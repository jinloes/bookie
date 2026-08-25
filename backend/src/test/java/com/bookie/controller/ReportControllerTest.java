package com.bookie.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.service.ReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReportService reportService;

  @Test
  void exposesAuthoritativeCashflowSummary() throws Exception {
    when(reportService.cashflow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 4L, 10L))
        .thenReturn(
            new ApiResponses.CashflowSummaryResponse(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("3000.00"),
                new BigDecimal("2700.00"),
                new BigDecimal("300.00"),
                List.of()));

    mockMvc
        .perform(get("/api/reports/cashflow?from=2026-01-01&to=2026-12-31&ownerId=4&activityId=10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalIncome").value(3000.0))
        .andExpect(jsonPath("$.totalExpenses").value(2700.0))
        .andExpect(jsonPath("$.netCashflow").value(300.0));
  }
}
