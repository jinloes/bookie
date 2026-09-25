package com.bookie.ledger.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

  private Long id(String sql) {
    return jdbcTemplate.queryForObject(sql, Long.class);
  }
}
