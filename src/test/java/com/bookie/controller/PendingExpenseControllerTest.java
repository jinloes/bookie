package com.bookie.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.service.PendingExpenseService;
import com.bookie.service.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(PendingExpenseController.class)
class PendingExpenseControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PendingExpenseService pendingExpenseService;
  @MockitoBean private SseService sseService;

  private PendingExpense pendingExpense() {
    return PendingExpense.builder()
        .id(1L)
        .subject("Water bill")
        .status(PendingExpenseStatus.READY)
        .build();
  }

  @Nested
  class List_ {

    @Test
    void returnsPendingExpenses() throws Exception {
      when(pendingExpenseService.findAll()).thenReturn(List.of(pendingExpense()));

      mockMvc
          .perform(get("/api/pending-expenses"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].subject").value("Water bill"));
    }
  }

  @Nested
  class Subscribe {

    @Test
    void returnsSseStream() throws Exception {
      when(sseService.subscribe()).thenReturn(new SseEmitter(0L));

      mockMvc
          .perform(get("/api/pending-expenses/events").accept(MediaType.TEXT_EVENT_STREAM))
          .andExpect(status().isOk());
    }
  }

  @Nested
  class Save {

    @Test
    void savesAndReturnsExpense() throws Exception {
      Expense expense =
          Expense.builder()
              .id(10L)
              .amount(BigDecimal.valueOf(120.00))
              .description("Water bill")
              .date(LocalDate.of(2024, 3, 1))
              .category(ExpenseCategory.UTILITIES)
              .build();
      when(pendingExpenseService.saveAsExpense(eq(1L), any())).thenReturn(expense);

      SavePendingExpenseRequest request =
          new SavePendingExpenseRequest(
              BigDecimal.valueOf(120.00),
              "Water bill",
              LocalDate.of(2024, 3, 1),
              "UTILITIES",
              null,
              null);

      mockMvc
          .perform(
              post("/api/pending-expenses/1/save")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.description").value("Water bill"));
    }
  }

  @Nested
  class Dismiss {

    @Test
    void deletesAndReturns204() throws Exception {
      mockMvc.perform(delete("/api/pending-expenses/1")).andExpect(status().isNoContent());

      verify(pendingExpenseService).dismiss(1L);
    }
  }
}
