package com.bookie.intake;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"bookie.auto-import.enabled=false", "bookie.intake.worker.enabled=false"})
@AutoConfigureMockMvc
class IntakeOpenApiContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void publishesDurableInboxVisibilityAndRetryContracts() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v2/inbox'].get.operationId").value("getInboxItems"))
        .andExpect(jsonPath("$.paths['/api/v2/inbox/{id}'].get.operationId").value("getInboxItem"))
        .andExpect(
            jsonPath("$.paths['/api/v2/inbox/{id}/jobs'].get.operationId")
                .value("getInboxItemJobs"))
        .andExpect(
            jsonPath("$.paths['/api/v2/inbox/jobs/{jobId}/retry'].post.operationId")
                .value("retryInboxJob"))
        .andExpect(jsonPath("$.components.schemas.InboxItemResponse.properties.state").exists())
        .andExpect(
            jsonPath("$.components.schemas.InboxItemResponse.properties.externalSyncState")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.InboxItemResponse.properties.immutableSourceId")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.BackgroundJobResponse.properties.leaseExpiresAt")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.BackgroundJobResponse.properties.terminalReason")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/v2/inbox/{id}/save'].post.operationId").value("saveInboxItem"))
        .andExpect(
            jsonPath("$.paths['/api/v2/inbox/{id}/reparse'].post.operationId")
                .value("reparseInboxItem"))
        .andExpect(
            jsonPath("$.paths['/api/v2/inbox/{id}'].delete.operationId").value("dismissInboxItem"))
        .andExpect(
            jsonPath("$.paths['/api/v2/inbox/events'].get.operationId")
                .value("subscribeInboxEvents"))
        .andExpect(
            jsonPath("$.paths['/api/v2/inbox/imports/venmo'].post.operationId")
                .value("importVenmoInboxItems"))
        .andExpect(
            jsonPath("$.components.schemas.SaveInboxItemRequest.properties.neutralCategoryId")
                .exists())
        .andExpect(jsonPath("$.components.schemas.InboxItemResponse.properties.sourceId").exists())
        .andExpect(
            jsonPath("$.components.schemas.InboxItemResponse.properties.legacySourceId")
                .doesNotExist())
        .andExpect(jsonPath("$.components.schemas.InboxItemResponse.properties.category").exists())
        .andExpect(
            jsonPath("$.components.schemas.VenmoImportResponse.properties.importedRows").exists())
        .andExpect(jsonPath("$.components.schemas.OutlookEmail.properties.inboxItemId").exists())
        .andExpect(jsonPath("$.components.schemas.OutlookEmail.properties.inboxState").exists())
        .andExpect(
            jsonPath("$.components.schemas.OutlookEmail.properties.pendingId").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/pending-expenses']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/expenses']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/incomes']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/agent/expense']").doesNotExist());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/expenses",
        "/api/incomes",
        "/api/incomes/pending",
        "/api/pending-expenses",
        "/api/payers",
        "/api/payers/keywords"
      })
  void retiredLegacyReadRoutesAreNotServed(String route) throws Exception {
    mockMvc.perform(get(route)).andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/expenses", "/api/incomes", "/api/agent/expense"})
  void retiredLegacyWriteRoutesAreNotServed(String route) throws Exception {
    mockMvc
        .perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNotFound());
  }
}
