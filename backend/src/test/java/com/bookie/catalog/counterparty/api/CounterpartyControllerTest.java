package com.bookie.catalog.counterparty.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.application.UpsertCounterpartyCommand;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CounterpartyController.class)
class CounterpartyControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private CounterpartyCatalog counterpartyCatalog;

  private Counterparty counterparty() {
    return Counterparty.builder()
        .id(1L)
        .name("Acme Corp")
        .type(CounterpartyType.COMPANY)
        .aliases(List.of("Acme"))
        .accounts(Set.of("ACC-001"))
        .build();
  }

  @Nested
  class Get {

    @Test
    void returnsCounterpartyList() throws Exception {
      when(counterpartyCatalog.findAll()).thenReturn(List.of(counterparty()));

      mockMvc
          .perform(get("/api/counterparties"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(1))
          .andExpect(jsonPath("$[0].name").value("Acme Corp"))
          .andExpect(jsonPath("$[0].type").value("COMPANY"))
          .andExpect(jsonPath("$[0].aliases[0]").value("Acme"))
          .andExpect(jsonPath("$[0].accounts[0]").value("ACC-001"));
    }

    @Test
    void returnsCounterpartyById() throws Exception {
      when(counterpartyCatalog.findById(1L)).thenReturn(counterparty());

      mockMvc
          .perform(get("/api/counterparties/1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.name").value("Acme Corp"));
    }
  }

  @Nested
  class Write {

    @Test
    void createsThroughCounterpartyCatalog() throws Exception {
      when(counterpartyCatalog.create(any())).thenReturn(counterparty());
      UpsertCounterpartyRequest request =
          new UpsertCounterpartyRequest(
              "Acme Corp", CounterpartyTypeDto.COMPANY, List.of("Acme"), Set.of("ACC-001"));

      mockMvc
          .perform(
              post("/api/counterparties")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updatesThroughCounterpartyCatalog() throws Exception {
      when(counterpartyCatalog.update(eq(1L), any())).thenReturn(counterparty());
      UpsertCounterpartyRequest request =
          new UpsertCounterpartyRequest(
              "Acme Corp", CounterpartyTypeDto.COMPANY, List.of("Acme"), Set.of("ACC-001"));

      mockMvc
          .perform(
              put("/api/counterparties/1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deletesThroughCounterpartyCatalog() throws Exception {
      doNothing().when(counterpartyCatalog).delete(1L);

      mockMvc.perform(delete("/api/counterparties/1")).andExpect(status().isNoContent());

      verify(counterpartyCatalog).delete(1L);
    }

    @Test
    void mapsTheRequestTypeToTheDomainType() throws Exception {
      when(counterpartyCatalog.create(any())).thenReturn(counterparty());

      mockMvc
          .perform(
              post("/api/counterparties")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"Pat\",\"type\":\"PERSON\",\"aliases\":[],\"accounts\":[]}"))
          .andExpect(status().isOk());

      verify(counterpartyCatalog)
          .create(
              new UpsertCounterpartyCommand("Pat", CounterpartyType.PERSON, List.of(), Set.of()));
    }
  }

  @Test
  void getTypesPreservesTheTypeOptionContract() throws Exception {
    mockMvc
        .perform(get("/api/counterparties/types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].value").value("PERSON"))
        .andExpect(jsonPath("$[0].label").value("Person"))
        .andExpect(jsonPath("$[1].value").value("COMPANY"))
        .andExpect(jsonPath("$[1].label").value("Company"));
  }
}
