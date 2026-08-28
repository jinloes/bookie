package com.bookie.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.integrations.outlook.OutlookAuthorization;
import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.service.EmailParseQueueService;
import com.bookie.service.OutlookService;
import com.bookie.service.PendingExpenseService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OutlookController.class)
class OutlookControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OutlookService outlookService;
  @MockitoBean private OutlookAuthorization outlookAuthorization;
  @MockitoBean private PendingExpenseService pendingExpenseService;
  @MockitoBean private EmailParseQueueService emailParseQueueService;

  @Nested
  class Callback {

    @Test
    void returnsHtmlWithRedirectToSettingsOnSuccessWhenStateIsValid() throws Exception {
      when(outlookAuthorization.validateState("valid-state")).thenReturn(true);

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

      verify(outlookAuthorization).handleCallback("auth-code");
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
      when(outlookAuthorization.validateState("bad-state")).thenReturn(false);

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

    @Nested
    class ParseEmail {

      @Test
      void forwardsExplicitActivityContextToPendingQueue() throws Exception {
        PendingExpense pending =
            PendingExpense.builder()
                .id(17L)
                .sourceId("msg-pay")
                .sourceType(ExpenseSource.OUTLOOK_EMAIL)
                .configuredActivityId(42L)
                .status(PendingExpenseStatus.PROCESSING)
                .build();
        when(pendingExpenseService.findOrCreate(
                "msg-pay", ExpenseSource.OUTLOOK_EMAIL, "Synthetic pay advice", 42L))
            .thenReturn(new PendingExpenseService.FindOrCreateResult(pending, false));

        mockMvc
            .perform(
                post("/api/outlook/emails/msg-pay/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"subject":"Synthetic pay advice","activityId":42}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(17))
            .andExpect(jsonPath("$.status").value("PROCESSING"));

        verify(emailParseQueueService).processEmail(17L, "msg-pay", 42L);
      }
    }
  }
}
