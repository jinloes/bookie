package com.bookie.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.service.EmailParseQueueService;
import com.bookie.service.MsalTokenService;
import com.bookie.service.OutlookService;
import com.bookie.service.PendingExpenseService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OutlookController.class)
class OutlookControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OutlookService outlookService;
  @MockitoBean private MsalTokenService msalTokenService;
  @MockitoBean private PendingExpenseService pendingExpenseService;
  @MockitoBean private EmailParseQueueService emailParseQueueService;

  @Nested
  class Callback {

    @Test
    void redirectsToRootOnSuccessWhenStateIsValid() throws Exception {
      when(msalTokenService.validateState("valid-state")).thenReturn(true);

      mockMvc
          .perform(
              get("/api/outlook/callback").param("code", "auth-code").param("state", "valid-state"))
          .andExpect(status().is3xxRedirection())
          .andExpect(header().string("Location", "/"));

      verify(msalTokenService).handleCallback("auth-code");
    }

    @Test
    void redirectsWithOutlookErrorWhenErrorParamPresent() throws Exception {
      mockMvc
          .perform(
              get("/api/outlook/callback")
                  .param("error", "access_denied")
                  .param("error_description", "user denied"))
          .andExpect(status().is3xxRedirection())
          .andExpect(header().string("Location", "/?outlookError=access_denied"));
    }

    @Test
    void redirectsWithStateMismatchWhenStateIsInvalid() throws Exception {
      when(msalTokenService.validateState("bad-state")).thenReturn(false);

      mockMvc
          .perform(
              get("/api/outlook/callback").param("code", "auth-code").param("state", "bad-state"))
          .andExpect(status().is3xxRedirection())
          .andExpect(header().string("Location", "/?outlookError=state_mismatch"));
    }
  }
}
