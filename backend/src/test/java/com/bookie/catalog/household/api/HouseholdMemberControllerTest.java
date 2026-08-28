package com.bookie.catalog.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.catalog.household.application.HouseholdCatalog;
import com.bookie.catalog.household.application.UpsertHouseholdMemberCommand;
import com.bookie.catalog.household.domain.HouseholdMember;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HouseholdMemberController.class)
class HouseholdMemberControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HouseholdCatalog householdCatalog;

  @Test
  void getAll_returnsMembers() throws Exception {
    when(householdCatalog.findAll())
        .thenReturn(List.of(HouseholdMember.builder().id(1L).name("Alex").active(true).build()));

    mockMvc
        .perform(get("/api/household-members"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Alex"))
        .andExpect(jsonPath("$[0].active").value(true));
  }

  @Test
  void create_validatesAndReturnsMember() throws Exception {
    when(householdCatalog.create(any()))
        .thenReturn(HouseholdMember.builder().id(2L).name("Sam").active(true).build());
    ArgumentCaptor<UpsertHouseholdMemberCommand> command =
        ArgumentCaptor.forClass(UpsertHouseholdMemberCommand.class);

    mockMvc
        .perform(
            post("/api/household-members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Sam","active":true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Sam"));

    verify(householdCatalog).create(command.capture());
    assertThat(command.getValue()).isEqualTo(new UpsertHouseholdMemberCommand("Sam", true));
  }

  @Test
  void updatePreservesHttpContract() throws Exception {
    when(householdCatalog.update(org.mockito.ArgumentMatchers.eq(2L), any()))
        .thenReturn(HouseholdMember.builder().id(2L).name("Taylor").active(false).build());

    mockMvc
        .perform(
            put("/api/household-members/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Taylor","active":false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Taylor"))
        .andExpect(jsonPath("$.active").value(false));
  }
}
