package com.bookie.catalog.financialcategory.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.application.FinancialCategoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinancialCategoryController.class)
class FinancialCategoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FinancialCategoryService financialCategoryService;

  @Test
  void filtersCatalogByDirectionAndActivity() throws Exception {
    NeutralCategory wages =
        NeutralCategory.builder()
            .id(33L)
            .key("WAGES")
            .label("Wages")
            .direction(TransactionDirection.INCOME)
            .active(true)
            .system(true)
            .build();
    when(financialCategoryService.findCompatible(TransactionDirection.INCOME, 10L))
        .thenReturn(List.of(new FinancialCategoryService.CompatibleCategory(wages, "W2", null)));

    mockMvc
        .perform(get("/api/financial-categories?direction=INCOME&activityId=10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("WAGES"))
        .andExpect(jsonPath("$[0].direction").value("INCOME"))
        .andExpect(jsonPath("$[0].taxTreatment").value("W2"))
        .andExpect(jsonPath("$[0].neutralCategoryId").value(33));
  }
}
