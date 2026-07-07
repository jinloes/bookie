package com.bookie.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.UpsertPayerRequest;
import com.bookie.service.PayerService;
import com.bookie.service.PropertyHistoryService;
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

@WebMvcTest(PayerController.class)
class PayerControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PayerService payerService;
  @MockitoBean private PropertyHistoryService propertyHistoryService;

  private Payer payer() {
    return Payer.builder()
        .id(1L)
        .name("Acme Corp")
        .type(PayerType.COMPANY)
        .aliases(List.of("Acme"))
        .accounts(Set.of("ACC-001"))
        .build();
  }

  @Nested
  class GetAll {

    @Test
    void returnsPayerList() throws Exception {
      when(payerService.findAll()).thenReturn(List.of(payer()));

      mockMvc
          .perform(get("/api/payers"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(1))
          .andExpect(jsonPath("$[0].name").value("Acme Corp"))
          .andExpect(jsonPath("$[0].type").value("COMPANY"));
    }
  }

  @Nested
  class GetById {

    @Test
    void returnsPayer() throws Exception {
      when(payerService.findById(1L)).thenReturn(payer());

      mockMvc
          .perform(get("/api/payers/1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.name").value("Acme Corp"));
    }
  }

  @Nested
  class Create {

    @Test
    void persistsAndReturnsPayer() throws Exception {
      when(payerService.create(any())).thenReturn(payer());

      UpsertPayerRequest req =
          new UpsertPayerRequest(
              "Acme Corp", PayerType.COMPANY, List.of("Acme"), Set.of("ACC-001"));

      mockMvc
          .perform(
              post("/api/payers")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.name").value("Acme Corp"));
    }
  }

  @Nested
  class Update {

    @Test
    void returnsUpdatedPayer() throws Exception {
      when(payerService.update(eq(1L), any())).thenReturn(payer());

      UpsertPayerRequest req =
          new UpsertPayerRequest(
              "Acme Corp", PayerType.COMPANY, List.of("Acme"), Set.of("ACC-001"));

      mockMvc
          .perform(
              put("/api/payers/1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1));
    }
  }

  @Nested
  class Delete {

    @Test
    void returnsNoContent() throws Exception {
      doNothing().when(payerService).delete(1L);

      mockMvc.perform(delete("/api/payers/1")).andExpect(status().isNoContent());
    }
  }

  @Nested
  class GetTypes {

    @Test
    void returnsAllPayerTypes() throws Exception {
      mockMvc
          .perform(get("/api/payers/types"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].value").exists())
          .andExpect(jsonPath("$[0].label").exists());
    }
  }
}
