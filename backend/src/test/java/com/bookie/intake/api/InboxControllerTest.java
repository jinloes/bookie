package com.bookie.intake.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.InboxCommands;
import com.bookie.intake.application.InboxEventStream;
import com.bookie.intake.application.InboxQueryService;
import com.bookie.intake.application.SaveInboxItemCommand;
import com.bookie.intake.application.VenmoImportCommand;
import com.bookie.intake.application.VenmoImportResult;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(InboxController.class)
@Import(InboxItemResponseAssembler.class)
class InboxControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InboxQueryService queryService;
  @MockitoBean private InboxCommands inboxCommands;
  @MockitoBean private InboxEventStream eventStream;
  @MockitoBean private BackgroundJobService backgroundJobService;
  @MockitoBean private ActivityCatalog activityCatalog;
  @MockitoBean private CategoryCatalog categoryCatalog;

  @Nested
  class FindActive {

    @Test
    void exposesActiveItemsWithProposalAndResolvedCatalogReferences() throws Exception {
      when(queryService.findActive()).thenReturn(List.of(readyItem()));
      when(activityCatalog.findAll()).thenReturn(List.of(activity()));
      when(categoryCatalog.findLegacyCategoryById(30L)).thenReturn(Optional.of(category()));

      mockMvc
          .perform(get("/api/v2/inbox"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(2))
          .andExpect(jsonPath("$[0].sourceId").value("receipt-file"))
          .andExpect(jsonPath("$[0].state").value("READY"))
          .andExpect(jsonPath("$[0].proposedDirection").value("EXPENSE"))
          .andExpect(jsonPath("$[0].amount").value(12.34))
          .andExpect(jsonPath("$[0].description").value("Synthetic supplies"))
          .andExpect(jsonPath("$[0].date").value("2026-08-20"))
          .andExpect(jsonPath("$[0].proposedCategory").value("SUPPLIES"))
          .andExpect(jsonPath("$[0].propertyName").value("Synthetic House"))
          .andExpect(jsonPath("$[0].counterpartyName").value("Synthetic Vendor"))
          .andExpect(jsonPath("$[0].sourceLabel").value("Synthetic label"))
          .andExpect(jsonPath("$[0].activity.id").value(10))
          .andExpect(jsonPath("$[0].category.id").value(30))
          .andExpect(jsonPath("$[0].category.neutralCategoryId").value(40))
          .andExpect(jsonPath("$[0].counterpartyId").value(50))
          .andExpect(jsonPath("$[0].classificationAmbiguous").value(true))
          .andExpect(jsonPath("$[0].receiptExternalId").value("receipt-file"))
          .andExpect(jsonPath("$[0].receiptFileName").value("receipt.pdf"));
    }

    @Test
    void leavesUnresolvedCatalogReferencesEmpty() throws Exception {
      when(queryService.findActive()).thenReturn(List.of(item()));
      when(activityCatalog.findAll()).thenReturn(List.of());

      mockMvc
          .perform(get("/api/v2/inbox"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].sourceId").value("legacy-message"))
          .andExpect(jsonPath("$[0].activity").value(nullValue()))
          .andExpect(jsonPath("$[0].category").value(nullValue()))
          .andExpect(jsonPath("$[0].externalSyncState").value("MANUAL_REVIEW"));
      verifyNoInteractions(categoryCatalog);
    }
  }

  @Nested
  class FindById {

    @Test
    void returnsOneAssembledItem() throws Exception {
      when(queryService.findById(1L)).thenReturn(item());
      when(activityCatalog.findAll()).thenReturn(List.of());

      mockMvc
          .perform(get("/api/v2/inbox/1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.state").value("SAVED"));
    }
  }

  @Nested
  class Subscribe {

    @Test
    void opensTheInboxEventStream() throws Exception {
      when(eventStream.subscribe()).thenReturn(new SseEmitter());

      mockMvc.perform(get("/api/v2/inbox/events")).andExpect(request().asyncStarted());

      verify(eventStream).subscribe();
    }
  }

  @Nested
  class Save {

    @Test
    void savesThroughTheInboxCommandPort() throws Exception {
      InboxItem saved = item();
      when(inboxCommands.save(eq(2L), any())).thenReturn(saved);
      when(activityCatalog.findAll()).thenReturn(List.of());

      mockMvc
          .perform(
              post("/api/v2/inbox/2/save")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"direction":"EXPENSE","amount":12.34,"date":"2026-08-20",
                       "description":"Synthetic supplies","activityId":10,
                       "neutralCategoryId":40,"counterpartyId":50,"sourceLabel":"label"}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("SAVED"));

      ArgumentCaptor<SaveInboxItemCommand> command =
          ArgumentCaptor.forClass(SaveInboxItemCommand.class);
      verify(inboxCommands).save(eq(2L), command.capture());
      assertThat(command.getValue())
          .isEqualTo(
              SaveInboxItemCommand.builder()
                  .direction(TransactionDirection.EXPENSE)
                  .amount(new BigDecimal("12.34"))
                  .date(LocalDate.of(2026, 8, 20))
                  .description("Synthetic supplies")
                  .activityId(10L)
                  .neutralCategoryId(40L)
                  .counterpartyId(50L)
                  .sourceLabel("label")
                  .build());
    }

    @Test
    void rejectsIncompleteProposalBeforeCallingThePort() throws Exception {
      mockMvc
          .perform(
              post("/api/v2/inbox/2/save")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"direction\":\"EXPENSE\",\"amount\":12.34}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(inboxCommands);
    }

    @Test
    void surfacesConflictsFromThePort() throws Exception {
      when(inboxCommands.save(eq(2L), any()))
          .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "processing"));

      mockMvc
          .perform(
              post("/api/v2/inbox/2/save")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"direction":"INCOME","amount":5,"date":"2026-08-20",
                       "description":"Income","activityId":10,"neutralCategoryId":41}
                      """))
          .andExpect(status().isConflict());
    }
  }

  @Nested
  class Reparse {

    @Test
    void returnsTheRequeuedItem() throws Exception {
      InboxItem queued = item();
      queued.setState(InboxState.QUEUED);
      when(inboxCommands.reparse(1L)).thenReturn(queued);
      when(activityCatalog.findAll()).thenReturn(List.of());

      mockMvc
          .perform(post("/api/v2/inbox/1/reparse"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("QUEUED"));
    }
  }

  @Nested
  class Dismiss {

    @Test
    void dismissesWithoutContent() throws Exception {
      mockMvc.perform(delete("/api/v2/inbox/1")).andExpect(status().isNoContent());

      verify(inboxCommands).dismiss(1L);
    }
  }

  @Nested
  class ImportVenmo {

    @Test
    void passesTheStatementAndOptionalFiltersToThePort() throws Exception {
      when(inboxCommands.importVenmo(any()))
          .thenReturn(
              VenmoImportResult.builder()
                  .totalRows(3)
                  .importedRows(1)
                  .skippedSenderRows(1)
                  .skippedOutgoingRows(1)
                  .senderFilter("Synthetic Payer")
                  .activityName("Rental")
                  .build());
      MockMultipartFile file =
          new MockMultipartFile("file", "venmo.csv", "text/csv", "csv".getBytes());

      mockMvc
          .perform(
              multipart("/api/v2/inbox/imports/venmo")
                  .file(file)
                  .param("counterpartyId", "50")
                  .param("activityId", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalRows").value(3))
          .andExpect(jsonPath("$.importedRows").value(1))
          .andExpect(jsonPath("$.skippedSenderRows").value(1))
          .andExpect(jsonPath("$.senderFilter").value("Synthetic Payer"));

      ArgumentCaptor<VenmoImportCommand> command =
          ArgumentCaptor.forClass(VenmoImportCommand.class);
      verify(inboxCommands).importVenmo(command.capture());
      assertThat(command.getValue().fileName()).isEqualTo("venmo.csv");
      assertThat(command.getValue().content()).isEqualTo("csv".getBytes());
      assertThat(command.getValue().counterpartyId()).isEqualTo(50L);
      assertThat(command.getValue().activityId()).isEqualTo(10L);
      assertThat(command.getValue().propertyId()).isNull();
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
    void resetsAnExplicitlyRetriedJobWhoseServicePublishesTheKick() throws Exception {
      BackgroundJob job = job();
      job.setState(BackgroundJobState.AVAILABLE);
      job.setAttempts(0);
      job.setTerminalReason(null);
      when(backgroundJobService.retry(9L)).thenReturn(job);

      mockMvc
          .perform(post("/api/v2/inbox/jobs/9/retry"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("AVAILABLE"));

      // BackgroundJobService.retry publishes InboxItemQueued; the controller kicks nothing itself.
      verify(backgroundJobService).retry(9L);
    }

    @Test
    void aRetriedJobWithoutAnInboxItemReportsANullInboxItemId() throws Exception {
      BackgroundJob job = job();
      job.setInboxItem(null);
      job.setSourceExternalId("drive-9");
      job.setType(BackgroundJobType.MOVE_RECEIPT);
      job.setState(BackgroundJobState.AVAILABLE);
      when(backgroundJobService.retry(9L)).thenReturn(job);

      mockMvc
          .perform(post("/api/v2/inbox/jobs/9/retry"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.inboxItemId").value(nullValue()))
          .andExpect(jsonPath("$.type").value("MOVE_RECEIPT"));
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

  private InboxItem readyItem() {
    return InboxItem.builder()
        .id(2L)
        .origin(ExpenseSource.RECEIPT)
        .legacySourceId("receipt-file")
        .state(InboxState.READY)
        .externalSyncState(ExternalSyncState.NOT_REQUIRED)
        .proposedDirection(TransactionDirection.EXPENSE)
        .rawStatus("READY")
        .subject("receipt.pdf")
        .proposedAmount(new BigDecimal("12.34"))
        .proposedDescription("Synthetic supplies")
        .proposedDate(LocalDate.of(2026, 8, 20))
        .proposedCategory("SUPPLIES")
        .proposedPropertyName("Synthetic House")
        .proposedCounterpartyName("Synthetic Vendor")
        .proposedSourceLabel("Synthetic label")
        .legacyActivityId(10L)
        .legacyCategoryId(30L)
        .legacyCounterpartyId(50L)
        .classificationAmbiguous(true)
        .receiptExternalId("receipt-file")
        .receiptFileName("receipt.pdf")
        .createdAt(LocalDateTime.of(2026, 8, 25, 10, 0))
        .updatedAt(LocalDateTime.of(2026, 8, 25, 11, 0))
        .version(0L)
        .build();
  }

  private FinancialActivity activity() {
    return FinancialActivity.builder()
        .id(10L)
        .name("Synthetic rental")
        .activityType(ActivityType.RENTAL)
        .taxTreatment(TaxTreatment.SCHEDULE_E)
        .active(true)
        .build();
  }

  private LegacyCategoryView category() {
    return LegacyCategoryView.builder()
        .id(30L)
        .key("SUPPLIES")
        .label("Supplies")
        .direction(TransactionDirection.EXPENSE)
        .taxTreatment(LegacyTaxTreatment.SCHEDULE_E)
        .active(true)
        .system(true)
        .neutralCategoryId(40L)
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
