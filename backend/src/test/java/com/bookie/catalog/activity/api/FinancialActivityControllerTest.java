package com.bookie.catalog.activity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.application.UpsertFinancialActivityCommand;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.household.domain.HouseholdMember;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinancialActivityController.class)
class FinancialActivityControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ActivityCatalog activityCatalog;

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
    when(activityCatalog.findAll()).thenReturn(List.of(teaching()));

    mockMvc
        .perform(get("/api/activities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Teaching — School District"))
        .andExpect(jsonPath("$[0].owner.name").value("Sam"))
        .andExpect(jsonPath("$[0].property").doesNotExist());
  }

  @Test
  void create_returnsActivity() throws Exception {
    when(activityCatalog.create(any())).thenReturn(teaching());
    ArgumentCaptor<UpsertFinancialActivityCommand> command =
        ArgumentCaptor.forClass(UpsertFinancialActivityCommand.class);

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

    verify(activityCatalog).create(command.capture());
    assertThat(command.getValue())
        .isEqualTo(
            new UpsertFinancialActivityCommand(
                "Teaching — School District",
                ActivityType.EMPLOYMENT,
                TaxTreatment.W2,
                1L,
                null,
                true));
  }

  @Test
  void getTypes_returnsSupportedTypes() throws Exception {
    mockMvc
        .perform(get("/api/activities/types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].value").value("RENTAL"));
  }

  @Test
  void updatePreservesHttpContract() throws Exception {
    when(activityCatalog.update(org.mockito.ArgumentMatchers.eq(2L), any())).thenReturn(teaching());

    mockMvc
        .perform(
            put("/api/activities/2")
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
        .andExpect(jsonPath("$.name").value("Teaching — School District"));
  }

  @Test
  void getTaxTreatmentsReturnsSupportedTreatments() throws Exception {
    mockMvc
        .perform(get("/api/activities/tax-treatments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].value").value("SCHEDULE_E"));
  }
}
