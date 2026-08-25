package com.bookie.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.model.FinancialCategory;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
import com.bookie.service.FinancialCategoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinancialCategoryController.class)
class FinancialCategoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FinancialCategoryService financialCategoryService;

  @Test
  void filtersCatalogByDirectionAndActivity() throws Exception {
    FinancialCategory wages =
        FinancialCategory.builder()
            .id(1L)
            .key("WAGES")
            .label("Wages")
            .direction(TransactionDirection.INCOME)
            .taxTreatment(TaxTreatment.W2)
            .active(true)
            .system(true)
            .build();
    when(financialCategoryService.findCompatible(TransactionDirection.INCOME, 10L))
        .thenReturn(List.of(wages));

    mockMvc
        .perform(get("/api/financial-categories?direction=INCOME&activityId=10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("WAGES"))
        .andExpect(jsonPath("$[0].direction").value("INCOME"))
        .andExpect(jsonPath("$[0].taxTreatment").value("W2"));
  }
}
