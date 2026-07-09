package com.bookie.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void returnsHtmlWithRedirectToSettingsOnSuccessWhenStateIsValid() throws Exception {
      when(msalTokenService.validateState("valid-state")).thenReturn(true);

      mockMvc
          .perform(
              get("/api/outlook/callback").param("code", "auth-code").param("state", "valid-state"))
          .andExpect(status().isOk())
          .andExpect(content().contentType("text/html;charset=UTF-8"))
          .andExpect(
              content().string(org.hamcrest.Matchers.containsString("localhost:5173/settings")))
          .andExpect(
              content()
                  .string(org.hamcrest.Matchers.containsString("meta http-equiv=\"refresh\"")));

      verify(msalTokenService).handleCallback("auth-code");
    }

    @Test
    void returnsHtmlWithErrorRedirectWhenErrorParamPresent() throws Exception {
      mockMvc
          .perform(
              get("/api/outlook/callback")
                  .param("error", "access_denied")
                  .param("error_description", "user denied"))
          .andExpect(status().isOk())
          .andExpect(content().contentType("text/html;charset=UTF-8"))
          .andExpect(
              content()
                  .string(
                      org.hamcrest.Matchers.containsString(
                          "localhost:5173/?outlookError=access_denied")))
          .andExpect(
              content().string(org.hamcrest.Matchers.containsString("window.location.href")));
    }

    @Test
    void returnsHtmlWithStateMismatchErrorWhenStateIsInvalid() throws Exception {
      when(msalTokenService.validateState("bad-state")).thenReturn(false);

      mockMvc
          .perform(
              get("/api/outlook/callback").param("code", "auth-code").param("state", "bad-state"))
          .andExpect(status().isOk())
          .andExpect(content().contentType("text/html;charset=UTF-8"))
          .andExpect(
              content()
                  .string(
                      org.hamcrest.Matchers.containsString(
                          "localhost:5173/?outlookError=state_mismatch")));
    }
  }

  @Nested
  class EmailContent {

    @Test
    void returnsOriginalEmailContent() throws Exception {
      when(outlookService.fetchMessageBody("msg-123"))
          .thenReturn(new OutlookService.MessageContent("Water Bill", "Body text", "2026-06-03"));

      mockMvc
          .perform(get("/api/outlook/emails/msg-123/content"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.subject").value("Water Bill"))
          .andExpect(jsonPath("$.body").value("Body text"))
          .andExpect(jsonPath("$.receivedDate").value("2026-06-03"));

      verify(outlookService).fetchMessageBody("msg-123");
    }
  }
}
