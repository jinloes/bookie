package com.bookie.intake.sources;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.integrations.outlook.OutlookAuthorization;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(OutlookController.class)
@Import(OutlookControllerTest.FixedClockConfiguration.class)
class OutlookControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OutlookService outlookService;
  @MockitoBean private OutlookAuthorization outlookAuthorization;
  @MockitoBean private OutlookEmailIntakeService outlookEmailIntakeService;

  @TestConfiguration
  static class FixedClockConfiguration {

    @Bean
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Nested
  class RentalEmails {

    @Test
    void usesTheClockYearWhenTheYearParameterIsOmitted() throws Exception {
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));

      mockMvc.perform(get("/api/outlook/emails/rental")).andExpect(status().isOk());

      verify(outlookService).getRentalEmails(0, 2026);
    }
  }

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
      void forwardsExplicitActivityContextAndReturnsTheQueuedInboxItems() throws Exception {
        when(outlookEmailIntakeService.queue("msg-pay", 42L))
            .thenReturn(
                new OutlookEmailIntakeService.QueueResult(
                    List.of(item(117L), item(118L), item(119L)), 3));

        mockMvc
            .perform(
                post("/api/outlook/emails/msg-pay/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"subject":"Synthetic pay advice","activityId":42}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(117))
            .andExpect(jsonPath("$.state").value("QUEUED"))
            .andExpect(jsonPath("$.count").value(3))
            .andExpect(jsonPath("$.ids[0]").value(117))
            .andExpect(jsonPath("$.ids[1]").value(118))
            .andExpect(jsonPath("$.ids[2]").value(119));

        verify(outlookEmailIntakeService).queue("msg-pay", 42L);
      }

      @Test
      void conflictsWhenEveryAttachmentIsAlreadyComplete() throws Exception {
        when(outlookEmailIntakeService.queue("msg-done", null))
            .thenReturn(new OutlookEmailIntakeService.QueueResult(List.of(), 0));

        mockMvc
            .perform(
                post("/api/outlook/emails/msg-done/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"subject\":\"Done\"}"))
            .andExpect(status().isConflict());
      }

      private InboxItem item(Long id) {
        return InboxItem.builder().id(id).state(InboxState.QUEUED).build();
      }

      @Test
      void returnsDiscoveryErrorWithoutACompatibilityShapedSuccess() throws Exception {
        when(outlookEmailIntakeService.queue("missing-message", null))
            .thenThrow(
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Outlook message not found: missing-message"));

        mockMvc
            .perform(
                post("/api/outlook/emails/missing-message/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"subject":"Missing"}
                        """))
            .andExpect(status().isNotFound());
      }
    }
  }
}
