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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClassificationHistoryController.class)
class ClassificationHistoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ClassificationHistory classificationHistory;

  @Test
  void servesPropertyKeywordHistory() throws Exception {
    when(classificationHistory.getAllPropertyKeywords())
        .thenReturn(
            List.of(
                new PropertyKeywordHistory(1L, "property-keyword", 10L, "Wild Indigo", 2, 1L),
                new PropertyKeywordHistory(2L, "orphan-property-keyword", null, null, 1, null)));

    mockMvc
        .perform(get("/api/properties/keywords"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].keyword").value("property-keyword"))
        .andExpect(jsonPath("$[0].property.id").value(10))
        .andExpect(jsonPath("$[0].property.name").value("Wild Indigo"))
        .andExpect(jsonPath("$[1].property").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void servesCounterpartyKeywordHistory() throws Exception {
    when(classificationHistory.getAllPayerKeywords())
        .thenReturn(
            List.of(
                new CounterpartyKeywordHistory(1L, "payer-keyword", 20L, "ACWD", 3, 1L),
                new CounterpartyKeywordHistory(2L, "orphan-payer-keyword", null, null, 1, null)));

    mockMvc
        .perform(get("/api/counterparties/keywords"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].keyword").value("payer-keyword"))
        .andExpect(jsonPath("$[0].payer.id").value(20))
        .andExpect(jsonPath("$[0].payer.name").value("ACWD"))
        .andExpect(jsonPath("$[1].payer").value(org.hamcrest.Matchers.nullValue()));
  }
}
