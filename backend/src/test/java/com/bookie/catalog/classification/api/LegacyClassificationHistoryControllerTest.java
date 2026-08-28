package com.bookie.catalog.classification.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.CounterpartyKeywordHistory;
import com.bookie.catalog.classification.application.PropertyKeywordHistory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LegacyClassificationHistoryController.class)
class LegacyClassificationHistoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ClassificationHistory classificationHistory;

  @Test
  void preservesPropertyKeywordHistoryRoute() throws Exception {
    when(classificationHistory.getAllPropertyKeywords())
        .thenReturn(List.of(new PropertyKeywordHistory(1L, "property-keyword", null, 2, null)));

    mockMvc
        .perform(get("/api/properties/keywords"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].keyword").value("property-keyword"));
  }

  @Test
  void preservesPayerKeywordHistoryRoute() throws Exception {
    when(classificationHistory.getAllPayerKeywords())
        .thenReturn(List.of(new CounterpartyKeywordHistory(1L, "payer-keyword", null, 3, null)));

    mockMvc
        .perform(get("/api/payers/keywords"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].keyword").value("payer-keyword"));
  }
}
