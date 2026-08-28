package com.bookie.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "bookie.auto-import.enabled=false")
@AutoConfigureMockMvc
class ReportingOpenApiContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void packageMovePreservesReportOperationsAndSchemas() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/reports/cashflow'].get.operationId").value("getCashflowReport"))
        .andExpect(
            jsonPath("$.paths['/api/reports/schedule-e'].get.operationId")
                .value("getScheduleEReport"))
        .andExpect(
            jsonPath("$.components.schemas.CashflowSummaryResponse.properties.from").exists())
        .andExpect(
            jsonPath("$.components.schemas.CashflowSummaryResponse.properties.activities").exists())
        .andExpect(
            jsonPath("$.components.schemas.ActivityCashflowResponse.properties.netCashflow")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.ScheduleEReportResponse.properties.activities").exists())
        .andExpect(
            jsonPath("$.components.schemas.ScheduleEActivityResponse.properties.categories")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.CategoryTotalResponse.properties.category").exists());
  }
}
