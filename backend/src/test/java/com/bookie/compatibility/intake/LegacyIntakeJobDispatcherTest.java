package com.bookie.compatibility.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.intake.application.JobExecutionException;
import com.bookie.intake.application.JobExecutionOutcome;
import com.bookie.intake.application.JobExecutionResult;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.integrations.documents.DocumentTextExtractor;
import com.bookie.integrations.outlook.OutlookMailPort;
import com.bookie.integrations.outlook.OutlookMessageIdentity;
import com.bookie.integrations.outlook.OutlookMoveResult;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseSource;
import com.bookie.service.EmailParserService;
import com.bookie.service.OutlookService;
import com.bookie.service.ParseQueueSupport;
import com.bookie.service.ReceiptService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LegacyIntakeJobDispatcherTest {

  @Mock private OutlookService outlookService;
  @Mock private OutlookMailPort outlookMail;
  @Mock private ReceiptService receiptService;
  @Mock private DocumentTextExtractor documentTextExtractor;
  @Mock private EmailParserService emailParserService;
  @Mock private ClassificationHistory classificationHistory;
  @Mock private ParseQueueSupport parseQueueSupport;

  @InjectMocks private LegacyIntakeJobDispatcher dispatcher;

  @Nested
  class ParseOutlook {

    @Test
    void parsesFromDurableIdentityAndConfiguredActivity() throws Exception {
      BackgroundJob job = job(BackgroundJobType.PARSE_OUTLOOK, "legacy-message");
      job.setLegacyPendingId(10L);
      job.getInboxItem().setConfiguredActivityId(42L);
      OutlookService.MessageContent message =
          new OutlookService.MessageContent("Subject", "Body", "2026-08-20");
      when(outlookService.fetchMessageBody("legacy-message")).thenReturn(message);
      EmailSuggestion suggestion =
          EmailSuggestion.builder()
              .emailType(EmailType.INCOME)
              .keywords(List.of("synthetic-keyword"))
              .build();
      when(emailParserService.suggestFromEmail("Subject", "Body", "2026-08-20", 42L))
          .thenReturn(suggestion);
      runParseTask();

      JobExecutionResult result = dispatcher.execute(job);

      assertThat(result.immutableSourceId()).isNull();
      verify(classificationHistory).storeKeywords("legacy-message", List.of("synthetic-keyword"));
    }
  }

  @Nested
  class ParseReceipt {

    @Test
    void checksThePersistedReceiptIdentityAndParsesBytes() throws Exception {
      BackgroundJob job = job(BackgroundJobType.PARSE_RECEIPT, "receipt-item");
      job.setLegacyPendingId(11L);
      when(receiptService.getReceiptName("receipt-item")).thenReturn("synthetic.pdf");
      when(receiptService.getReceiptContent("receipt-item"))
          .thenReturn(new ByteArrayInputStream("pdf".getBytes()));
      when(documentTextExtractor.extractText(any(byte[].class), eq("synthetic.pdf")))
          .thenReturn("extracted");
      EmailSuggestion suggestion =
          EmailSuggestion.builder()
              .emailType(EmailType.EXPENSE)
              .keywords(List.of("receipt-keyword"))
              .build();
      when(emailParserService.suggestFromReceipt("synthetic.pdf", "extracted", null))
          .thenReturn(suggestion);
      runParseTask();

      dispatcher.execute(job);

      verify(emailParserService).suggestFromReceipt("synthetic.pdf", "extracted", null);
      verify(classificationHistory).storeKeywords("receipt-item", List.of("receipt-keyword"));
    }
  }

  @Nested
  class TranslateOutlookId {

    @Test
    void retainsLegacyIdAndReturnsImmutableId() {
      BackgroundJob job = job(BackgroundJobType.TRANSLATE_OUTLOOK_ID, "legacy-message");
      when(outlookMail.translateLegacyIds(List.of("legacy-message")))
          .thenReturn(List.of(new OutlookMessageIdentity("legacy-message", "immutable-message")));

      JobExecutionResult result = dispatcher.execute(job);

      assertThat(result.immutableSourceId()).isEqualTo("immutable-message");
      assertThat(job.getInboxItem().getLegacySourceId()).isEqualTo("legacy-message");
    }

    @Test
    void partialTranslationRequiresManualReview() {
      BackgroundJob job = job(BackgroundJobType.TRANSLATE_OUTLOOK_ID, "legacy-message");
      when(outlookMail.translateLegacyIds(List.of("legacy-message"))).thenReturn(List.of());

      assertThatThrownBy(() -> dispatcher.execute(job))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              failure ->
                  assertThat(((JobExecutionException) failure).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW));
    }

    @Test
    void previouslyResolvedIdentityCompletesWithoutAnotherRemoteTranslation() {
      BackgroundJob job = job(BackgroundJobType.TRANSLATE_OUTLOOK_ID, "legacy-message");
      job.getInboxItem().setImmutableSourceId("immutable-message");

      JobExecutionResult result = dispatcher.execute(job);

      assertThat(result.immutableSourceId()).isEqualTo("immutable-message");
      verify(outlookMail, never()).translateLegacyIds(any());
    }

    @Test
    void authenticationFailureRequiresManualReview() {
      BackgroundJob job = job(BackgroundJobType.TRANSLATE_OUTLOOK_ID, "legacy-message");
      when(outlookMail.translateLegacyIds(List.of("legacy-message")))
          .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Reconnect Outlook"));

      assertThatThrownBy(() -> dispatcher.execute(job))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              failure ->
                  assertThat(((JobExecutionException) failure).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW));
    }

    @Test
    void batchesTranslationsAndIsolatesMissingResults() {
      BackgroundJob translatedJob = job(BackgroundJobType.TRANSLATE_OUTLOOK_ID, "legacy-one");
      translatedJob.setId(21L);
      BackgroundJob missingJob = job(BackgroundJobType.TRANSLATE_OUTLOOK_ID, "legacy-two");
      missingJob.setId(22L);
      when(outlookMail.translateLegacyIds(List.of("legacy-one", "legacy-two")))
          .thenReturn(List.of(new OutlookMessageIdentity("legacy-one", "immutable-one")));

      Map<Long, JobExecutionOutcome> outcomes =
          dispatcher.executeBatch(List.of(translatedJob, missingJob));

      assertThat(outcomes.get(21L).result().immutableSourceId()).isEqualTo("immutable-one");
      assertThat(outcomes.get(22L).failure())
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              failure ->
                  assertThat(((JobExecutionException) failure).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW));
      verify(outlookMail).translateLegacyIds(List.of("legacy-one", "legacy-two"));
    }

    @Test
    void batchAuthenticationFailureRequiresManualReviewForEveryJob() {
      BackgroundJob first = job(BackgroundJobType.TRANSLATE_OUTLOOK_ID, "legacy-one");
      first.setId(23L);
      BackgroundJob second = job(BackgroundJobType.TRANSLATE_OUTLOOK_ID, "legacy-two");
      second.setId(24L);
      when(outlookMail.translateLegacyIds(List.of("legacy-one", "legacy-two")))
          .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Not configured"));

      Map<Long, JobExecutionOutcome> outcomes = dispatcher.executeBatch(List.of(first, second));

      assertThat(outcomes.values())
          .extracting(JobExecutionOutcome::failure)
          .allSatisfy(
              failure ->
                  assertThat(failure)
                      .isInstanceOf(JobExecutionException.class)
                      .satisfies(
                          mapped ->
                              assertThat(((JobExecutionException) mapped).getKind())
                                  .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW)));
    }
  }

  @Nested
  class MoveOutlook {

    @Test
    void successfulMoveRecordsImmutableIdentityWithoutReplacingLegacyId() {
      BackgroundJob job = job(BackgroundJobType.MOVE_OUTLOOK, "legacy-message");
      job.getInboxItem().setImmutableSourceId("immutable-message");
      when(outlookService.moveEmailIdentityIfConfigured(
              new OutlookMessageIdentity("legacy-message", "immutable-message")))
          .thenReturn(
              Optional.of(
                  new OutlookMoveResult(
                      OutlookMoveResult.Status.MOVED,
                      new OutlookMessageIdentity("legacy-message", "immutable-message"))));

      JobExecutionResult result = dispatcher.execute(job);

      assertThat(result.immutableSourceId()).isEqualTo("immutable-message");
      assertThat(job.getInboxItem().getLegacySourceId()).isEqualTo("legacy-message");
    }

    @Test
    void disabledOrAlreadySatisfiedMoveCompletesIdempotently() {
      BackgroundJob job = job(BackgroundJobType.MOVE_OUTLOOK, "legacy-message");
      when(outlookService.moveEmailIdentityIfConfigured(
              new OutlookMessageIdentity("legacy-message", null)))
          .thenReturn(Optional.empty());

      assertThat(dispatcher.execute(job)).isEqualTo(JobExecutionResult.completed());
    }

    @Test
    void alreadyMovedMessageStillRecordsItsResolvedImmutableIdentity() {
      BackgroundJob job = job(BackgroundJobType.MOVE_OUTLOOK, "legacy-message");
      when(outlookService.moveEmailIdentityIfConfigured(
              new OutlookMessageIdentity("legacy-message", null)))
          .thenReturn(
              Optional.of(
                  new OutlookMoveResult(
                      OutlookMoveResult.Status.ALREADY_AT_DESTINATION,
                      new OutlookMessageIdentity("legacy-message", "immutable-message"))));

      assertThat(dispatcher.execute(job).immutableSourceId()).isEqualTo("immutable-message");
    }
  }

  @Nested
  class MoveReceipt {

    @Test
    void verifiesChecksumBeforeDeterministicYearMove() {
      BackgroundJob job = job(BackgroundJobType.MOVE_RECEIPT, "receipt-item");
      job.setTargetYear(2026);
      job.getInboxItem()
          .addArtifact(
              InboxArtifact.builder()
                  .type(InboxArtifactType.RECEIPT)
                  .externalId("receipt-item")
                  .sha256("a".repeat(64))
                  .build());
      when(receiptService.hasReceiptChecksum("receipt-item", "a".repeat(64))).thenReturn(true);

      dispatcher.execute(job);

      verify(receiptService).moveTaxesFolderForJob("receipt-item", 2026);
    }

    @Test
    void checksumConflictIsTerminalManualReviewEvidence() {
      BackgroundJob job = job(BackgroundJobType.MOVE_RECEIPT, "receipt-item");
      job.setTargetYear(2026);
      job.getInboxItem()
          .addArtifact(
              InboxArtifact.builder()
                  .type(InboxArtifactType.RECEIPT)
                  .externalId("receipt-item")
                  .sha256("a".repeat(64))
                  .build());
      when(receiptService.hasReceiptChecksum("receipt-item", "a".repeat(64))).thenReturn(false);

      assertThatThrownBy(() -> dispatcher.execute(job))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              failure ->
                  assertThat(((JobExecutionException) failure).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW));
    }

    @Test
    void missingDurableArtifactRequiresManualReview() {
      BackgroundJob job = job(BackgroundJobType.MOVE_RECEIPT, "receipt-item");
      job.setTargetYear(2026);

      assertThatThrownBy(() -> dispatcher.execute(job))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              failure ->
                  assertThat(((JobExecutionException) failure).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW));

      verify(receiptService, never()).hasReceiptChecksum(any(), any());
      verify(receiptService, never()).moveTaxesFolderForJob(any(), anyInt());
    }

    @Test
    void artifactForDifferentRemoteItemRequiresManualReview() {
      BackgroundJob job = job(BackgroundJobType.MOVE_RECEIPT, "receipt-item");
      job.setTargetYear(2026);
      job.getInboxItem()
          .addArtifact(
              InboxArtifact.builder()
                  .type(InboxArtifactType.RECEIPT)
                  .externalId("different-item")
                  .sha256("a".repeat(64))
                  .build());

      assertThatThrownBy(() -> dispatcher.execute(job))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              failure ->
                  assertThat(((JobExecutionException) failure).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW));

      verify(receiptService, never()).hasReceiptChecksum(any(), any());
    }

    @Test
    void missingPersistedChecksumRequiresManualReview() {
      BackgroundJob job = job(BackgroundJobType.MOVE_RECEIPT, "receipt-item");
      job.setTargetYear(2026);
      job.getInboxItem()
          .addArtifact(
              InboxArtifact.builder()
                  .type(InboxArtifactType.RECEIPT)
                  .externalId("receipt-item")
                  .build());

      assertThatThrownBy(() -> dispatcher.execute(job))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              failure ->
                  assertThat(((JobExecutionException) failure).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW));

      verify(receiptService, never()).hasReceiptChecksum(any(), any());
    }

    @Test
    void retryableIntegrationFailureIsTranslatedAtTheIntakePort() {
      BackgroundJob job = job(BackgroundJobType.MOVE_RECEIPT, "receipt-item");
      job.setTargetYear(2026);
      job.getInboxItem()
          .addArtifact(
              InboxArtifact.builder()
                  .type(InboxArtifactType.RECEIPT)
                  .externalId("receipt-item")
                  .sha256("a".repeat(64))
                  .build());
      when(receiptService.hasReceiptChecksum("receipt-item", "a".repeat(64))).thenReturn(true);
      IntegrationException failure =
          IntegrationException.builder()
              .kind(IntegrationFailureKind.RATE_LIMITED)
              .message("retry later")
              .build();
      org.mockito.Mockito.doThrow(failure)
          .when(receiptService)
          .moveTaxesFolderForJob("receipt-item", 2026);

      assertThatThrownBy(() -> dispatcher.execute(job))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              translated ->
                  assertThat(((JobExecutionException) translated).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.RETRYABLE));
    }

    @Test
    void missingDestinationYearRequiresManualReview() {
      BackgroundJob job = job(BackgroundJobType.MOVE_RECEIPT, "receipt-item");

      assertThatThrownBy(() -> dispatcher.execute(job))
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              failure ->
                  assertThat(((JobExecutionException) failure).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW));
    }
  }

  private void runParseTask() throws Exception {
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Callable<EmailSuggestion> task = invocation.getArgument(2);
              task.call();
              return null;
            })
        .when(parseQueueSupport)
        .run(any(), any(), any());
  }

  private BackgroundJob job(BackgroundJobType type, String sourceId) {
    InboxItem item =
        InboxItem.builder()
            .id(1L)
            .origin(
                type == BackgroundJobType.PARSE_RECEIPT || type == BackgroundJobType.MOVE_RECEIPT
                    ? ExpenseSource.RECEIPT
                    : ExpenseSource.OUTLOOK_EMAIL)
            .legacySourceId(sourceId)
            .state(type.isParsing() ? InboxState.PROCESSING : InboxState.SAVED)
            .externalSyncState(
                type.isExternalSync()
                    ? ExternalSyncState.PROCESSING
                    : ExternalSyncState.NOT_REQUIRED)
            .build();
    return BackgroundJob.builder().id(2L).inboxItem(item).type(type).build();
  }
}
