package com.bookie.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogOpenApiContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void packageMovePreservesCatalogOperationsAndSchemas() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/activities'].get.operationId").value("getFinancialActivities"))
        .andExpect(
            jsonPath("$.paths['/api/activities'].post.operationId")
                .value("createFinancialActivity"))
        .andExpect(
            jsonPath("$.paths['/api/household-members'].get.operationId")
                .value("getHouseholdMembers"))
        .andExpect(
            jsonPath("$.paths['/api/household-members'].post.operationId")
                .value("createHouseholdMember"))
        .andExpect(jsonPath("$.paths['/api/properties'].get.operationId").value("getProperties"))
        .andExpect(jsonPath("$.paths['/api/properties'].post.operationId").value("createProperty"))
        .andExpect(
            jsonPath("$.paths['/api/properties/keywords'].get.operationId")
                .value("getPropertyKeywords"))
        .andExpect(jsonPath("$.paths['/api/payers'].get.operationId").value("getPayers"))
        .andExpect(jsonPath("$.paths['/api/payers'].post.operationId").value("createPayer"))
        .andExpect(
            jsonPath("$.paths['/api/payers/keywords'].get.operationId").value("getPayerKeywords"))
        .andExpect(
            jsonPath("$.components.schemas.FinancialActivityResponse.properties.activityType")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.FinancialActivityResponse.properties.taxTreatment")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.FinancialActivityResponse.properties.owner").exists())
        .andExpect(
            jsonPath("$.components.schemas.FinancialActivityResponse.properties.property").exists())
        .andExpect(
            jsonPath("$.components.schemas.HouseholdMemberResponse.properties.name").exists())
        .andExpect(
            jsonPath("$.components.schemas.UpsertFinancialActivityRequest.properties.ownerId")
                .exists())
        .andExpect(
            jsonPath("$.components.schemas.UpsertHouseholdMemberRequest.properties.active")
                .exists())
        .andExpect(jsonPath("$.components.schemas.PropertyResponse.properties.accounts").exists())
        .andExpect(jsonPath("$.components.schemas.CreatePropertyRequest.properties.type").exists())
        .andExpect(jsonPath("$.components.schemas.PayerResponse.properties.aliases").exists())
        .andExpect(jsonPath("$.components.schemas.PayerResponse.properties.accounts").exists())
        .andExpect(jsonPath("$.components.schemas.UpsertPayerRequest.properties.type").exists())
        .andExpect(
            jsonPath("$.components.schemas.EmailKeywordPayerHistory.properties.payer").exists())
        .andExpect(
            jsonPath("$.components.schemas.EmailKeywordPropertyHistory.properties.property")
                .exists())
        .andExpect(jsonPath("$.components.schemas.CounterpartyKeywordHistory").doesNotExist())
        .andExpect(jsonPath("$.components.schemas.PropertyKeywordHistory").doesNotExist());
  }
}
