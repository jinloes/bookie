package com.bookie.intake;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
                .exists());
  }
}
