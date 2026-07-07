package com.bookie.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bookie.model.CreateExpenseRequest;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.model.UpdateExpenseRequest;
import com.bookie.service.ExpenseService;
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

@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ExpenseService expenseService;

  private Expense expense() {
    Property property =
        Property.builder()
            .id(1L)
            .name("123 Main St")
            .address("123 Main St")
            .type(PropertyType.SINGLE_FAMILY)
            .build();
    return Expense.builder()
        .id(1L)
        .amount(new BigDecimal("500.00"))
        .description("Roof repair")
        .date(LocalDate.of(2024, 1, 15))
        .category(ExpenseCategory.REPAIRS)
        .property(property)
        .build();
  }

  @Test
  void getAll_returnsExpenseList() throws Exception {
    when(expenseService.findAll()).thenReturn(List.of(expense()));

    mockMvc
        .perform(get("/api/expenses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].description").value("Roof repair"));
  }

  @Test
  void getById_returnsExpense() throws Exception {
    when(expenseService.findById(1L)).thenReturn(expense());

    mockMvc
        .perform(get("/api/expenses/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.category").value("REPAIRS"));
  }

  @Test
  void create_persistsAndReturnsExpense() throws Exception {
    when(expenseService.create(any())).thenReturn(expense());

    CreateExpenseRequest req =
        new CreateExpenseRequest(
            new BigDecimal("500.00"),
            "Roof repair",
            LocalDate.of(2024, 1, 15),
            ExpenseCategory.REPAIRS,
            1L,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/api/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void create_withNullDate_returnsBadRequest() throws Exception {
    String body =
        """
        {
          "amount": 500.00,
          "description": "Roof repair",
          "date": null,
          "category": "REPAIRS"
        }
        """;

    mockMvc
        .perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.details.date").exists());

    verify(expenseService, never()).create(any());
  }

  @Test
  void update_returnsUpdatedExpense() throws Exception {
    when(expenseService.update(eq(1L), any(UpdateExpenseRequest.class))).thenReturn(expense());

    UpdateExpenseRequest req =
        new UpdateExpenseRequest(
            new BigDecimal("500.00"),
            "Roof repair",
            LocalDate.of(2024, 1, 15),
            ExpenseCategory.REPAIRS,
            1L,
            null,
            null,
            null);

    mockMvc
        .perform(
            put("/api/expenses/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void delete_returnsNoContent() throws Exception {
    doNothing().when(expenseService).delete(1L);

    mockMvc.perform(delete("/api/expenses/1")).andExpect(status().isNoContent());
  }

  @Test
  void getTotal_returnsTotalAmount() throws Exception {
    when(expenseService.getTotalExpenses()).thenReturn(new BigDecimal("1250.00"));

    mockMvc
        .perform(get("/api/expenses/total"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1250.00));
  }

  @Test
  void getCategories_returnsAllScheduleECategories() throws Exception {
    mockMvc
        .perform(get("/api/expenses/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].value").value("ADVERTISING"))
        .andExpect(jsonPath("$[0].scheduleELine").value(5))
        .andExpect(jsonPath("$[14].value").value("OTHER"));
  }
}
