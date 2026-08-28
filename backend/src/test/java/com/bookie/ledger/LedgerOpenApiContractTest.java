package com.bookie.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "bookie.auto-import.enabled=false")
@AutoConfigureMockMvc
class LedgerOpenApiContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void publishesUnifiedTransactionCrudAndNormalizedSchemas() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v2/transactions'].get.operationId").value("getTransactions"))
        .andExpect(
            jsonPath("$.paths['/api/v2/transactions'].post.operationId").value("createTransaction"))
        .andExpect(
            jsonPath("$.paths['/api/v2/transactions/{id}'].get.operationId")
                .value("getTransactionById"))
        .andExpect(
            jsonPath("$.paths['/api/v2/transactions/{id}'].put.operationId")
                .value("updateTransaction"))
        .andExpect(
            jsonPath("$.paths['/api/v2/transactions/{id}'].delete.operationId")
                .value("deleteTransaction"))
        .andExpect(
            jsonPath("$.components.schemas.TransactionResponse.properties.direction").exists())
        .andExpect(
            jsonPath("$.components.schemas.TransactionResponse.properties.propertyId").exists())
        .andExpect(
            jsonPath("$.components.schemas.TransactionResponse.properties.attachments").exists())
        .andExpect(
            jsonPath("$.components.schemas.TransactionResponse.properties.importReferences")
                .exists())
        .andExpect(jsonPath("$.components.schemas.UpdateTransactionRequest.required").isArray());
  }
}
