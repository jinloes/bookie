package com.bookie.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bookie.model.Income;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.service.IncomeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IncomeController.class)
class IncomeControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private IncomeService incomeService;

  private Income income() {
    Property property =
        Property.builder()
            .id(1L)
            .name("123 Main St")
            .address("123 Main St")
            .type(PropertyType.SINGLE_FAMILY)
            .build();
    return Income.builder()
        .id(1L)
        .amount(new BigDecimal("1200.00"))
        .description("Monthly rent")
        .date(LocalDate.of(2024, 1, 1))
        .source("Rent")
        .property(property)
        .build();
  }

  @Test
  void getAll_returnsIncomeList() throws Exception {
    when(incomeService.findAll()).thenReturn(List.of(income()));

    mockMvc
        .perform(get("/api/incomes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].description").value("Monthly rent"));
  }

  @Test
  void getById_returnsIncome() throws Exception {
    when(incomeService.findById(1L)).thenReturn(income());

    mockMvc
        .perform(get("/api/incomes/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.source").value("Rent"));
  }

  @Test
  void create_persistsAndReturnsIncome() throws Exception {
    when(incomeService.save(any())).thenReturn(income());

    mockMvc
        .perform(
            post("/api/incomes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(income())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void update_returnsUpdatedIncome() throws Exception {
    when(incomeService.update(eq(1L), any())).thenReturn(income());

    mockMvc
        .perform(
            put("/api/incomes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(income())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void delete_returnsNoContent() throws Exception {
    doNothing().when(incomeService).delete(1L);

    mockMvc.perform(delete("/api/incomes/1")).andExpect(status().isNoContent());
  }

  @Test
  void getTotal_returnsTotalAmount() throws Exception {
    when(incomeService.getTotalIncome()).thenReturn(new BigDecimal("3600.00"));

    mockMvc
        .perform(get("/api/incomes/total"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3600.00));
  }
}
