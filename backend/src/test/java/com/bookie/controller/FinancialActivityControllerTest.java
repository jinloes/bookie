package com.bookie.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.model.ActivityType;
import com.bookie.model.FinancialActivity;
import com.bookie.model.HouseholdMember;
import com.bookie.model.TaxTreatment;
import com.bookie.service.FinancialActivityService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinancialActivityController.class)
class FinancialActivityControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FinancialActivityService financialActivityService;

  private FinancialActivity teaching() {
    return FinancialActivity.builder()
        .id(2L)
        .name("Teaching — School District")
        .activityType(ActivityType.EMPLOYMENT)
        .taxTreatment(TaxTreatment.W2)
        .owner(HouseholdMember.builder().id(1L).name("Sam").active(true).build())
        .active(true)
        .build();
  }

  @Test
  void getAll_returnsActivitiesWithOwner() throws Exception {
    when(financialActivityService.findAll()).thenReturn(List.of(teaching()));

    mockMvc
        .perform(get("/api/activities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Teaching — School District"))
        .andExpect(jsonPath("$[0].owner.name").value("Sam"))
        .andExpect(jsonPath("$[0].property").doesNotExist());
  }

  @Test
  void create_returnsActivity() throws Exception {
    when(financialActivityService.create(any())).thenReturn(teaching());

    mockMvc
        .perform(
            post("/api/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Teaching — School District",
                      "activityType": "EMPLOYMENT",
                      "taxTreatment": "W2",
                      "ownerId": 1,
                      "propertyId": null,
                      "active": true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taxTreatment").value("W2"));
  }

  @Test
  void getTypes_returnsSupportedTypes() throws Exception {
    mockMvc
        .perform(get("/api/activities/types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].value").value("RENTAL"));
  }
}
