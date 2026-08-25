package com.bookie.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.model.HouseholdMember;
import com.bookie.service.HouseholdMemberService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HouseholdMemberController.class)
class HouseholdMemberControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HouseholdMemberService householdMemberService;

  @Test
  void getAll_returnsMembers() throws Exception {
    when(householdMemberService.findAll())
        .thenReturn(List.of(HouseholdMember.builder().id(1L).name("Alex").active(true).build()));

    mockMvc
        .perform(get("/api/household-members"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Alex"))
        .andExpect(jsonPath("$[0].active").value(true));
  }

  @Test
  void create_validatesAndReturnsMember() throws Exception {
    when(householdMemberService.create(any()))
        .thenReturn(HouseholdMember.builder().id(2L).name("Sam").active(true).build());

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
  }
}
