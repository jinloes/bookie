package com.bookie.compatibility.intake;

import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.intake.application.IntakeJobDispatcher;
import com.bookie.intake.application.JobExecutionException;
import com.bookie.intake.application.JobExecutionOutcome;
import com.bookie.intake.application.JobExecutionResult;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.integrations.documents.DocumentTextExtractor;
import com.bookie.integrations.outlook.OutlookMailPort;
import com.bookie.integrations.outlook.OutlookMessageIdentity;
import com.bookie.integrations.outlook.OutlookMoveResult;
import com.bookie.model.ExpenseSource;
import com.bookie.service.EmailParserService;
import com.bookie.service.OutlookService;
import com.bookie.service.ParseQueueSupport;
import com.bookie.service.ReceiptService;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
class LegacyIntakeJobDispatcher implements IntakeJobDispatcher {

  private static final String DEFAULT_RECEIPT_SUBJECT = "Vendor Receipt / Invoice";

  private final OutlookService outlookService;
  private final OutlookMailPort outlookMail;
  private final ReceiptService receiptService;
  private final DocumentTextExtractor documentTextExtractor;
  private final EmailParserService emailParserService;
  private final ClassificationHistory classificationHistory;
  private final ParseQueueSupport parseQueueSupport;

  @Override
  public JobExecutionResult execute(BackgroundJob job) {
    try {
      return switch (job.getType()) {
        case PARSE_OUTLOOK -> parseOutlook(job);
        case PARSE_RECEIPT -> parseReceipt(job);
        case TRANSLATE_OUTLOOK_ID -> translateOutlookId(job);
        case MOVE_OUTLOOK -> moveOutlook(job);
        case MOVE_RECEIPT -> moveReceipt(job);
      };
    } catch (IntegrationException failure) {
      throw jobFailure(failure);
    } catch (ResponseStatusException failure) {
      throw jobFailure(failure);
    }
  }

  @Override
  public Map<Long, JobExecutionOutcome> executeBatch(List<BackgroundJob> jobs) {
    if (jobs.stream().anyMatch(job -> job.getType() != BackgroundJobType.TRANSLATE_OUTLOOK_ID)) {
      return IntakeJobDispatcher.super.executeBatch(jobs);
    }
    return translateOutlookIds(jobs);
  }

  private JobExecutionResult parseOutlook(BackgroundJob job) {
    Long pendingId = requiredPendingId(job);
    OutlookArtifact outlookArtifact = requiredOutlookArtifact(job);
    parseQueueSupport.run(
        pendingId,
        ExpenseSource.OUTLOOK_EMAIL,
        () -> {
          OutlookService.MessageContent message =
              outlookService.fetchMessageContent(
                  outlookArtifact.messageId(), outlookArtifact.attachmentId());
          var suggestion =
              emailParserService.suggestFromEmail(
                  message.subject(),
                  message.body(),
                  message.receivedDate(),
                  job.getInboxItem().getConfiguredActivityId());
          classificationHistory.storeKeywords(requiredLegacySourceId(job), suggestion.keywords());
          return suggestion;
        });
    return JobExecutionResult.completed();
  }

  private JobExecutionResult parseReceipt(BackgroundJob job) {
    Long pendingId = requiredPendingId(job);
    String itemId = requiredLegacySourceId(job);
    parseQueueSupport.run(
        pendingId,
        ExpenseSource.RECEIPT,
        () -> {
          String receiptName = receiptService.getReceiptName(itemId);
          String subject = StringUtils.defaultIfBlank(receiptName, DEFAULT_RECEIPT_SUBJECT);
          byte[] content;
          try (InputStream stream = receiptService.getReceiptContent(itemId)) {
            content = stream == null ? new byte[0] : stream.readAllBytes();
          }
          String text = documentTextExtractor.extractText(content, receiptName);
          var suggestion = emailParserService.suggestFromReceipt(subject, text, null);
          classificationHistory.storeKeywords(itemId, suggestion.keywords());
          return suggestion;
        });
    return JobExecutionResult.completed();
  }

  private JobExecutionResult translateOutlookId(BackgroundJob job) {
    if (StringUtils.isNotBlank(job.getInboxItem().getImmutableSourceId())) {
      return JobExecutionResult.withImmutableSourceId(job.getInboxItem().getImmutableSourceId());
    }
    String legacyId = requiredOutlookArtifact(job).messageId();
    List<OutlookMessageIdentity> translated = outlookMail.translateLegacyIds(List.of(legacyId));
    if (translated.size() != 1
        || !legacyId.equals(translated.getFirst().legacyId())
        || StringUtils.isBlank(translated.getFirst().immutableId())) {
      throw JobExecutionException.manualReview(
          "Outlook ID translation did not return one unambiguous immutable identity", null);
    }
    return JobExecutionResult.withImmutableSourceId(translated.getFirst().immutableId());
  }

  private Map<Long, JobExecutionOutcome> translateOutlookIds(List<BackgroundJob> jobs) {
    Map<Long, JobExecutionOutcome> outcomes = new LinkedHashMap<>();
    Map<String, List<BackgroundJob>> jobsByLegacyId = new LinkedHashMap<>();
    for (BackgroundJob job : jobs) {
      if (StringUtils.isNotBlank(job.getInboxItem().getImmutableSourceId())) {
        outcomes.put(
            job.getId(),
            JobExecutionOutcome.succeeded(
                JobExecutionResult.withImmutableSourceId(
                    job.getInboxItem().getImmutableSourceId())));
        continue;
      }
      try {
        jobsByLegacyId
            .computeIfAbsent(requiredOutlookArtifact(job).messageId(), ignored -> new ArrayList<>())
            .add(job);
      } catch (Exception failure) {
        outcomes.put(job.getId(), JobExecutionOutcome.failed(failure));
      }
    }

    List<String> requestedIds = new ArrayList<>();
    requestedIds.addAll(jobsByLegacyId.keySet());
    if (requestedIds.isEmpty()) {
      return outcomes;
    }

    List<OutlookMessageIdentity> translated;
    try {
      translated = outlookMail.translateLegacyIds(requestedIds);
    } catch (Exception failure) {
      Exception mappedFailure =
          failure instanceof IntegrationException integrationFailure
              ? jobFailure(integrationFailure)
              : failure instanceof ResponseStatusException responseFailure
                  ? jobFailure(responseFailure)
                  : failure;
      requestedIds.forEach(
          legacyId ->
              jobsByLegacyId
                  .get(legacyId)
                  .forEach(
                      job -> outcomes.put(job.getId(), JobExecutionOutcome.failed(mappedFailure))));
      return outcomes;
    }

    Map<String, List<OutlookMessageIdentity>> identitiesByLegacyId = new LinkedHashMap<>();
    if (translated != null) {
      translated.stream()
          .filter(identity -> identity != null && StringUtils.isNotBlank(identity.legacyId()))
          .forEach(
              identity ->
                  identitiesByLegacyId
                      .computeIfAbsent(identity.legacyId(), ignored -> new ArrayList<>())
                      .add(identity));
    }
    for (String legacyId : requestedIds) {
      List<OutlookMessageIdentity> identities =
          identitiesByLegacyId.getOrDefault(legacyId, List.of());
      if (identities.size() == 1 && StringUtils.isNotBlank(identities.getFirst().immutableId())) {
        jobsByLegacyId
            .get(legacyId)
            .forEach(
                job ->
                    outcomes.put(
                        job.getId(),
                        JobExecutionOutcome.succeeded(
                            JobExecutionResult.withImmutableSourceId(
                                identities.getFirst().immutableId()))));
      } else {
        JobExecutionException failure =
            JobExecutionException.manualReview(
                "Outlook ID translation did not return one unambiguous immutable identity", null);
        jobsByLegacyId
            .get(legacyId)
            .forEach(job -> outcomes.put(job.getId(), JobExecutionOutcome.failed(failure)));
      }
    }
    return outcomes;
  }

  private JobExecutionResult moveOutlook(BackgroundJob job) {
    String legacyId = requiredOutlookArtifact(job).messageId();
    if (outlookService.hasPendingItems(legacyId)) {
      return JobExecutionResult.completed();
    }
    return outlookService
        .moveEmailIdentityIfConfigured(
            new OutlookMessageIdentity(legacyId, job.getInboxItem().getImmutableSourceId()))
        .map(OutlookMoveResult::identity)
        .map(OutlookMessageIdentity::immutableId)
        .filter(StringUtils::isNotBlank)
        .map(JobExecutionResult::withImmutableSourceId)
        .orElseGet(JobExecutionResult::completed);
  }

  private JobExecutionResult moveReceipt(BackgroundJob job) {
    String itemId = requiredLegacySourceId(job);
    if (job.getTargetYear() == null) {
      throw JobExecutionException.manualReview(
          "Receipt move is missing its deterministic destination year", null);
    }
    InboxArtifact receiptArtifact =
        job.getInboxItem().getArtifacts().stream()
            .filter(artifact -> artifact.getType() == InboxArtifactType.RECEIPT)
            .filter(artifact -> itemId.equals(artifact.getExternalId()))
            .findFirst()
            .orElseThrow(
                () ->
                    JobExecutionException.manualReview(
                        "Receipt move is missing a durable artifact matching its source identity",
                        null));
    String expectedSha256 = receiptArtifact.getSha256();
    if (StringUtils.isBlank(expectedSha256)) {
      throw JobExecutionException.manualReview(
          "Receipt move is missing its persisted content checksum", null);
    }
    if (!receiptService.hasReceiptChecksum(itemId, expectedSha256)) {
      throw IntegrationException.builder()
          .kind(IntegrationFailureKind.CONFLICT)
          .message("Receipt content changed before its deterministic move")
          .build();
    }
    receiptService.moveTaxesFolderForJob(itemId, job.getTargetYear());
    return JobExecutionResult.completed();
  }

  private Long requiredPendingId(BackgroundJob job) {
    if (job.getLegacyPendingId() == null) {
      throw JobExecutionException.terminal("Parse job is missing its legacy pending ID", null);
    }
    return job.getLegacyPendingId();
  }

  private String requiredLegacySourceId(BackgroundJob job) {
    String sourceId = job.getInboxItem().getLegacySourceId();
    if (StringUtils.isBlank(sourceId)) {
      throw JobExecutionException.manualReview(
          "Intake job is missing its legacy source identity", null);
    }
    return sourceId;
  }

  private OutlookArtifact requiredOutlookArtifact(BackgroundJob job) {
    return job.getInboxItem().getArtifacts().stream()
        .filter(artifact -> artifact.getType() == InboxArtifactType.OUTLOOK_EMAIL)
        .map(
            artifact ->
                new OutlookArtifact(
                    artifact.getExternalId(), artifact.getTextValue(), artifact.getFileName()))
        .filter(artifact -> StringUtils.isNotBlank(artifact.messageId()))
        .findFirst()
        .orElseGet(() -> new OutlookArtifact(requiredLegacySourceId(job), null, null));
  }

  private record OutlookArtifact(String messageId, String attachmentId, String attachmentName) {}

  private JobExecutionException jobFailure(IntegrationException failure) {
    return failure.isRetryable()
        ? JobExecutionException.retryable(failure.getMessage(), failure)
        : JobExecutionException.manualReview(failure.getMessage(), failure);
  }

  private JobExecutionException jobFailure(ResponseStatusException failure) {
    String message = StringUtils.defaultIfBlank(failure.getReason(), failure.getMessage());
    return JobExecutionException.manualReview(message, failure);
  }
}
