package com.bookie.ledger.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.ledger.application.LedgerTransactionInput;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.model.TransactionDirection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private LedgerTransactionService transactionService;

  @Test
  void getAllReturnsUnifiedTransactions() throws Exception {
    when(transactionService.findAll()).thenReturn(List.of(transaction()));

    mockMvc
        .perform(get("/api/v2/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(31))
        .andExpect(jsonPath("$[0].direction").value("INCOME"))
        .andExpect(jsonPath("$[0].amount").value(1250.25))
        .andExpect(jsonPath("$[0].category.key").value("OTHER_INCOME"))
        .andExpect(jsonPath("$[0].version").value(2));
  }

  @Test
  void createValidatesAndDelegatesToLedgerCommandLayer() throws Exception {
    when(transactionService.create(any())).thenReturn(transaction());
    CreateTransactionRequest request =
        new CreateTransactionRequest(
            new BigDecimal("1250.25"),
            TransactionDirection.INCOME,
            LocalDate.of(2026, 8, 1),
            "August income",
            4L,
            7L,
            9L,
            null,
            null,
            "Synthetic source",
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/api/v2/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(31));

    verify(transactionService).create(any(LedgerTransactionInput.class));
  }

  @Test
  void createRejectsNonPositiveAmountBeforeWriting() throws Exception {
    String request =
        """
        {
          "amount": 0,
          "direction": "EXPENSE",
          "date": "2026-08-01",
          "description": "Invalid",
          "activityId": 4,
          "neutralCategoryId": 7
        }
        """;

    mockMvc
        .perform(
            post("/api/v2/transactions").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest());

    verify(transactionService, never()).create(any());
  }

  @Test
  void updateCarriesOptimisticVersion() throws Exception {
    when(transactionService.update(eq(31L), eq(2L), any())).thenReturn(transaction());
    UpdateTransactionRequest request =
        new UpdateTransactionRequest(
            2L,
            new BigDecimal("1250.25"),
            TransactionDirection.INCOME,
            LocalDate.of(2026, 8, 1),
            "August income",
            4L,
            7L,
            9L,
            null,
            null,
            "Synthetic source",
            null,
            null,
            null);

    mockMvc
        .perform(
            put("/api/v2/transactions/31")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));

    verify(transactionService).update(eq(31L), eq(2L), any());
  }

  @Test
  void deleteRequiresVersionAndReturnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/api/v2/transactions/31").param("version", "2"))
        .andExpect(status().isNoContent());

    verify(transactionService).delete(31L, 2L);
  }

  private FinancialTransaction transaction() {
    FinancialActivity activity =
        FinancialActivity.builder()
            .id(4L)
            .name("Consulting")
            .activityType(ActivityType.SELF_EMPLOYMENT)
            .taxTreatment(TaxTreatment.SCHEDULE_C)
            .active(true)
            .build();
    NeutralCategory category =
        NeutralCategory.builder()
            .id(7L)
            .key("OTHER_INCOME")
            .label("Other income")
            .direction(TransactionDirection.INCOME)
            .active(true)
            .system(true)
            .build();
    return FinancialTransaction.builder()
        .id(31L)
        .amount(new BigDecimal("1250.25"))
        .direction(TransactionDirection.INCOME)
        .date(LocalDate.of(2026, 8, 1))
        .description("August income")
        .activity(activity)
        .neutralCategory(category)
        .counterpartyId(9L)
        .createdAt(LocalDateTime.of(2026, 8, 1, 12, 0))
        .updatedAt(LocalDateTime.of(2026, 8, 1, 12, 5))
        .version(2L)
        .attachments(new LinkedHashSet<>())
        .importReferences(new LinkedHashSet<>())
        .build();
  }
}
