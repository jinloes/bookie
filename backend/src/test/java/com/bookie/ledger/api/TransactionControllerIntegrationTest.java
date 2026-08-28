package com.bookie.ledger.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.model.TransactionDirection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:transaction_controller;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.open-in-view=false",
      "bookie.auto-import.enabled=false",
      "bookie.ledger.read-mode=UNIFIED",
      "bookie.intake.worker.enabled=false"
    })
@AutoConfigureMockMvc
class TransactionControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Nested
  class GetTransactions {

    @Test
    void serializesActivityOwnerAfterTheLedgerReadTransactionCloses() throws Exception {
      Long activityId =
          id("SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'");
      Long neutralCategoryId =
          id("SELECT id FROM neutral_categories WHERE category_key = 'OTHER_INCOME'");
      Long ownerId =
          jdbcTemplate.queryForObject(
              "SELECT owner_id FROM financial_activities WHERE id = ?", Long.class, activityId);
      String ownerName =
          jdbcTemplate.queryForObject(
              """
                                    SELECT member.name
                                    FROM household_members member
                                    JOIN financial_activities activity ON activity.owner_id = member.id
                                    WHERE activity.id = ?
                                    """,
              String.class,
              activityId);
      String request =
          """
                            {
                              "amount": 1250.25,
                              "direction": "INCOME",
                              "date": "2026-08-27",
                              "description": "Detached owner regression",
                              "activityId": %d,
                              "neutralCategoryId": %d
                            }
                            """
              .formatted(activityId, neutralCategoryId);

      mockMvc
          .perform(
              post("/api/v2/transactions").contentType(MediaType.APPLICATION_JSON).content(request))
          .andExpect(status().isOk());
      Long transactionId =
          id(
              """
                                    SELECT id
                                    FROM financial_transactions
                                    WHERE description = 'Detached owner regression'
                                    """);

      mockMvc
          .perform(get("/api/v2/transactions"))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$[?(@.id == %d)].activity.owner.name".formatted(transactionId))
                  .value(hasItem(ownerName)));
      mockMvc
          .perform(get("/api/v2/transactions/{id}", transactionId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.activity.owner.id").value(ownerId))
          .andExpect(jsonPath("$.activity.owner.name").value(ownerName));
    }
  }

  @Nested
  class GetLegacyTransactionsInUnifiedMode {

    @Test
    void serializesActivityOwnerAndPreservesLegacyTieOrderForUnifiedReads() throws Exception {
      Long activityId =
          id("SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'");
      Long incomeCategoryId =
          id("SELECT id FROM neutral_categories WHERE category_key = 'OTHER_INCOME'");
      Long expenseCategoryId =
          id("SELECT id FROM neutral_categories WHERE category_key = 'OTHER_EXPENSE'");
      String ownerName =
          jdbcTemplate.queryForObject(
              """
              SELECT member.name
              FROM household_members member
              JOIN financial_activities activity ON activity.owner_id = member.id
              WHERE activity.id = ?
              """,
              String.class,
              activityId);

      createTransaction(
          TransactionDirection.INCOME,
          "Unified legacy income owner regression",
          activityId,
          incomeCategoryId);
      createTransaction(
          TransactionDirection.INCOME,
          "Unified legacy income order regression",
          activityId,
          incomeCategoryId);
      createTransaction(
          TransactionDirection.EXPENSE,
          "Unified legacy expense owner regression",
          activityId,
          expenseCategoryId);
      createTransaction(
          TransactionDirection.EXPENSE,
          "Unified legacy expense order regression",
          activityId,
          expenseCategoryId);
      Long incomeId =
          id(
              """
              SELECT legacy_id
              FROM legacy_transaction_map
              WHERE legacy_table = 'INCOMES'
                AND transaction_id = (
                  SELECT id
                  FROM financial_transactions
                  WHERE description = 'Unified legacy income owner regression'
                )
              """);
      Long laterIncomeId =
          id(
              """
              SELECT legacy_id
              FROM legacy_transaction_map
              WHERE legacy_table = 'INCOMES'
                AND transaction_id = (
                  SELECT id
                  FROM financial_transactions
                  WHERE description = 'Unified legacy income order regression'
                )
              """);
      Long expenseId =
          id(
              """
              SELECT legacy_id
              FROM legacy_transaction_map
              WHERE legacy_table = 'EXPENSES'
                AND transaction_id = (
                  SELECT id
                  FROM financial_transactions
                  WHERE description = 'Unified legacy expense owner regression'
                )
              """);
      Long laterExpenseId =
          id(
              """
              SELECT legacy_id
              FROM legacy_transaction_map
              WHERE legacy_table = 'EXPENSES'
                AND transaction_id = (
                  SELECT id
                  FROM financial_transactions
                  WHERE description = 'Unified legacy expense order regression'
                )
              """);

      mockMvc
          .perform(get("/api/incomes"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(incomeId))
          .andExpect(jsonPath("$[1].id").value(laterIncomeId))
          .andExpect(
              jsonPath("$[?(@.id == %d)].activity.owner.name".formatted(incomeId))
                  .value(hasItem(ownerName)));
      mockMvc
          .perform(get("/api/incomes/{id}", incomeId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.activity.owner.name").value(ownerName));
      mockMvc
          .perform(get("/api/expenses"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(expenseId))
          .andExpect(jsonPath("$[1].id").value(laterExpenseId))
          .andExpect(
              jsonPath("$[?(@.id == %d)].activity.owner.name".formatted(expenseId))
                  .value(hasItem(ownerName)));
      mockMvc
          .perform(get("/api/expenses/{id}", expenseId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.activity.owner.name").value(ownerName));
    }
  }

  private void createTransaction(
      TransactionDirection direction, String description, Long activityId, Long categoryId)
      throws Exception {
    String request =
        """
        {
          "amount": 1.00,
          "direction": "%s",
          "date": "2099-12-30",
          "description": "%s",
          "activityId": %d,
          "neutralCategoryId": %d
        }
        """
            .formatted(direction, description, activityId, categoryId);
    mockMvc
        .perform(
            post("/api/v2/transactions").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isOk());
  }

  private Long id(String sql) {
    return jdbcTemplate.queryForObject(sql, Long.class);
  }
}
