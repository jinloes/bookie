package com.bookie.intake.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.InboxQueryService;
import com.bookie.intake.application.IntakeJobKickoff;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.model.ExpenseSource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InboxController.class)
class InboxControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InboxQueryService queryService;
  @MockitoBean private BackgroundJobService backgroundJobService;
  @MockitoBean private IntakeJobKickoff jobKickoff;

  @Nested
  class FindAll {

    @Test
    void exposesWorkflowAndExternalSyncState() throws Exception {
      when(queryService.findAll()).thenReturn(List.of(item()));

      mockMvc
          .perform(get("/api/v2/inbox"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(1))
          .andExpect(jsonPath("$[0].legacySourceId").value("legacy-message"))
          .andExpect(jsonPath("$[0].state").value("SAVED"))
          .andExpect(jsonPath("$[0].externalSyncState").value("MANUAL_REVIEW"));
    }
  }

  @Nested
  class FindJobs {

    @Test
    void exposesTerminalFailureWithoutHidingAttempts() throws Exception {
      BackgroundJob job = job();
      when(queryService.findJobs(1L)).thenReturn(List.of(job));

      mockMvc
          .perform(get("/api/v2/inbox/1/jobs"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].type").value("MOVE_OUTLOOK"))
          .andExpect(jsonPath("$[0].state").value("TERMINAL"))
          .andExpect(jsonPath("$[0].attempts").value(5))
          .andExpect(jsonPath("$[0].terminalReason").value("MANUAL_REVIEW"));
    }
  }

  @Nested
  class Retry {

    @Test
    void resetsAndKicksExplicitlyRetriedJob() throws Exception {
      BackgroundJob job = job();
      job.setState(BackgroundJobState.AVAILABLE);
      job.setAttempts(0);
      job.setTerminalReason(null);
      when(backgroundJobService.retry(9L)).thenReturn(job);

      mockMvc
          .perform(post("/api/v2/inbox/jobs/9/retry"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("AVAILABLE"));

      verify(jobKickoff).runJob(9L);
    }
  }

  private InboxItem item() {
    return InboxItem.builder()
        .id(1L)
        .origin(ExpenseSource.OUTLOOK_EMAIL)
        .legacySourceId("legacy-message")
        .state(InboxState.SAVED)
        .externalSyncState(ExternalSyncState.MANUAL_REVIEW)
        .rawStatus("READY")
        .createdAt(LocalDateTime.of(2026, 8, 25, 10, 0))
        .updatedAt(LocalDateTime.of(2026, 8, 25, 11, 0))
        .version(2L)
        .build();
  }

  private BackgroundJob job() {
    return BackgroundJob.builder()
        .id(9L)
        .inboxItem(item())
        .type(BackgroundJobType.MOVE_OUTLOOK)
        .state(BackgroundJobState.TERMINAL)
        .attempts(5)
        .maxAttempts(5)
        .availableAt(LocalDateTime.of(2026, 8, 25, 10, 0))
        .lastError("Reconnect Outlook")
        .terminalReason("MANUAL_REVIEW")
        .createdAt(LocalDateTime.of(2026, 8, 25, 10, 0))
        .updatedAt(LocalDateTime.of(2026, 8, 25, 11, 0))
        .version(3L)
        .build();
  }
}
