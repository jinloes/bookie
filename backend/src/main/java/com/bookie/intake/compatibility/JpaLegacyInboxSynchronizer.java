package com.bookie.intake.compatibility;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.BackgroundJobStore;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.application.LegacyInboxMapStore;
import com.bookie.intake.application.LegacyInboxSnapshot;
import com.bookie.intake.application.LegacyInboxSynchronizer;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.InboxStateMachine;
import com.bookie.intake.domain.LegacyInboxMap;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.model.ExpenseSource;
import com.bookie.repository.ReceiptHashRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class JpaLegacyInboxSynchronizer implements LegacyInboxSynchronizer {

  private static final int DEFAULT_MAX_ATTEMPTS = 11;

  private final InboxItemStore inboxItemStore;
  private final LegacyInboxMapStore legacyInboxMapStore;
  private final BackgroundJobStore backgroundJobStore;
  private final LedgerTransactionService ledgerTransactionService;
  private final ReceiptHashRepository receiptHashRepository;
  private final Clock clock;
  private final BackgroundJobService backgroundJobService;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void created(LegacyPendingKey key, LegacyInboxSnapshot snapshot, boolean parsingRequired) {
    InboxItem item = resolveItem(key, snapshot);
    applySnapshot(item, snapshot);
    if (item.getState() == null) {
      item.setState(InboxState.RECEIVED);
    } else if (item.getState() == InboxState.FAILED || item.getState() == InboxState.DISMISSED) {
      InboxStateMachine.transition(item, InboxState.RECEIVED);
    }
    InboxState target = parsingRequired ? InboxState.QUEUED : InboxState.READY;
    if (item.getState() == InboxState.RECEIVED) {
      InboxStateMachine.transition(item, target);
    }
    item = saveWithMap(key, item);
    if (parsingRequired) {
      resetJob(item, key, parseJobType(snapshot), null);
    }
    enqueueTranslationIfNeeded(item, key);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void ready(LegacyPendingKey key, LegacyInboxSnapshot snapshot) {
    backgroundJobService.requireCurrentParse(key);
    updateState(key, snapshot, InboxState.READY);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void failed(LegacyPendingKey key, LegacyInboxSnapshot snapshot) {
    backgroundJobService.requireCurrentParse(key);
    updateState(key, snapshot, InboxState.FAILED);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void retryQueued(LegacyPendingKey key, LegacyInboxSnapshot snapshot) {
    InboxItem item = updateState(key, snapshot, InboxState.QUEUED);
    resetJob(item, key, parseJobType(snapshot), null);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void savePending(LegacyPendingKey key, LegacyInboxSnapshot snapshot) {
    updateState(key, snapshot, InboxState.SAVE_PENDING);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void saved(
      LegacyPendingKey key,
      LegacyInboxSnapshot snapshot,
      LegacyTransactionKey legacyTransactionKey) {
    InboxItem item = requireItem(key);
    applySnapshot(item, snapshot);
    Long transactionId =
        ledgerTransactionService
            .findByLegacyKey(legacyTransactionKey)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Saved financial record is missing its unified ledger mapping"))
            .getId();
    item.setFinancialTransactionId(transactionId);
    InboxStateMachine.transition(item, InboxState.SAVED);
    if (item.getOrigin() == ExpenseSource.OUTLOOK_EMAIL) {
      item.setExternalSyncState(ExternalSyncState.PENDING);
      item = saveWithMap(key, item);
      enqueueTranslationIfNeeded(item, key);
      resetJob(item, key, BackgroundJobType.MOVE_OUTLOOK, null);
    } else if (item.getOrigin() == ExpenseSource.RECEIPT) {
      item.setExternalSyncState(ExternalSyncState.PENDING);
      item = saveWithMap(key, item);
      Integer targetYear =
          snapshot.getProposedDate() == null ? null : snapshot.getProposedDate().getYear();
      resetJob(item, key, BackgroundJobType.MOVE_RECEIPT, targetYear);
    } else {
      item.setExternalSyncState(ExternalSyncState.NOT_REQUIRED);
      saveWithMap(key, item);
    }
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void dismissed(LegacyPendingKey key, LegacyInboxSnapshot snapshot) {
    InboxItem item = requireItem(key);
    applySnapshot(item, snapshot);
    InboxStateMachine.transition(item, InboxState.DISMISSED);
    if (item.getOrigin() == ExpenseSource.OUTLOOK_EMAIL) {
      item.setExternalSyncState(ExternalSyncState.PENDING);
    }
    item = saveWithMap(key, item);
    backgroundJobStore.terminalizeActiveForInbox(
        item.getId(), "DISMISSED", LocalDateTime.now(clock));
    if (item.getOrigin() == ExpenseSource.OUTLOOK_EMAIL) {
      enqueueTranslationIfNeeded(item, key);
      resetJob(item, key, BackgroundJobType.MOVE_OUTLOOK, null);
    }
  }

  private InboxItem updateState(
      LegacyPendingKey key, LegacyInboxSnapshot snapshot, InboxState target) {
    InboxItem item = requireItem(key);
    applySnapshot(item, snapshot);
    InboxStateMachine.transition(item, target);
    return saveWithMap(key, item);
  }

  private InboxItem resolveItem(LegacyPendingKey key, LegacyInboxSnapshot snapshot) {
    Optional<InboxItem> mapped =
        legacyInboxMapStore.findByKey(key).map(LegacyInboxMap::getInboxItem);
    if (mapped.isPresent()) {
      return mapped.get();
    }
    ExpenseSource origin = normalizedOrigin(snapshot.getOrigin());
    Optional<InboxItem> bySource =
        StringUtils.isBlank(snapshot.getLegacySourceId())
            ? Optional.empty()
            : inboxItemStore.findBySourceIdentity(origin, snapshot.getLegacySourceId());
    if (bySource.isPresent()) {
      InboxItem item = bySource.get();
      item.setCreatedAt(snapshot.getCreatedAt());
      return item;
    }
    return InboxItem.builder()
        .origin(origin)
        .state(InboxState.RECEIVED)
        .externalSyncState(ExternalSyncState.NOT_REQUIRED)
        .migrationLegacyTable(key.getTable().name())
        .migrationLegacyId(key.getId())
        .createdAt(snapshot.getCreatedAt())
        .build();
  }

  private InboxItem requireItem(LegacyPendingKey key) {
    return legacyInboxMapStore
        .findByKey(key)
        .map(LegacyInboxMap::getInboxItem)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Legacy pending record is missing its inbox mapping: "
                        + key.getTable()
                        + "/"
                        + key.getId()));
  }

  private InboxItem saveWithMap(LegacyPendingKey key, InboxItem item) {
    InboxItem saved = inboxItemStore.save(item);
    LegacyInboxMap mapping =
        legacyInboxMapStore
            .findByKey(key)
            .orElseGet(
                () ->
                    LegacyInboxMap.builder()
                        .key(key)
                        .inboxItem(saved)
                        .mappedAt(LocalDateTime.now(clock))
                        .build());
    mapping.setInboxItem(saved);
    legacyInboxMapStore.save(mapping);
    return saved;
  }

  private void applySnapshot(InboxItem item, LegacyInboxSnapshot snapshot) {
    item.setOrigin(normalizedOrigin(snapshot.getOrigin()));
    item.setLegacySourceType(snapshot.getLegacySourceType());
    item.setLegacySourceId(snapshot.getLegacySourceId());
    item.setRawStatus(snapshot.getRawStatus());
    item.setProposedDirection(snapshot.getProposedDirection());
    item.setSubject(snapshot.getSubject());
    item.setProposedAmount(snapshot.getProposedAmount());
    item.setProposedDescription(snapshot.getProposedDescription());
    item.setProposedDate(snapshot.getProposedDate());
    item.setProposedCategory(snapshot.getProposedCategory());
    item.setProposedPropertyName(snapshot.getProposedPropertyName());
    item.setProposedCounterpartyName(snapshot.getProposedCounterpartyName());
    item.setProposedSourceLabel(snapshot.getProposedSourceLabel());
    item.setLegacyActivityId(snapshot.getLegacyActivityId());
    item.setLegacyCategoryId(snapshot.getLegacyCategoryId());
    item.setLegacyPropertyId(snapshot.getLegacyPropertyId());
    item.setLegacyCounterpartyId(snapshot.getLegacyCounterpartyId());
    item.setConfiguredActivityId(snapshot.getConfiguredActivityId());
    item.setClassificationAmbiguous(snapshot.isClassificationAmbiguous());
    item.setErrorMessage(snapshot.getErrorMessage());
    item.setReceiptExternalId(snapshot.getReceiptExternalId());
    item.setReceiptFileName(snapshot.getReceiptFileName());
    if (item.getCreatedAt() == null) {
      item.setCreatedAt(snapshot.getCreatedAt());
    }
    replaceArtifacts(item, snapshot);
  }

  private void replaceArtifacts(InboxItem item, LegacyInboxSnapshot snapshot) {
    item.removeArtifacts(InboxArtifactType.UNRECOGNIZED_ALIAS);
    snapshot.getUnrecognizedAliases().stream()
        .map(
            alias ->
                InboxArtifact.builder()
                    .type(InboxArtifactType.UNRECOGNIZED_ALIAS)
                    .textValue(alias)
                    .build())
        .forEach(item::addArtifact);

    item.removeArtifacts(InboxArtifactType.RECEIPT);
    if (StringUtils.isNotBlank(snapshot.getReceiptExternalId())
        || StringUtils.isNotBlank(snapshot.getReceiptFileName())) {
      String sha256 =
          Optional.ofNullable(snapshot.getReceiptExternalId())
              .flatMap(receiptHashRepository::findByDriveItemId)
              .map(com.bookie.model.ReceiptHash::getSha256)
              .orElse(null);
      item.addArtifact(
          InboxArtifact.builder()
              .type(InboxArtifactType.RECEIPT)
              .externalId(snapshot.getReceiptExternalId())
              .fileName(snapshot.getReceiptFileName())
              .sha256(sha256)
              .build());
    }

    item.removeArtifacts(InboxArtifactType.OUTLOOK_EMAIL);
    if (StringUtils.isNotBlank(snapshot.getOutlookMessageId())) {
      item.addArtifact(
          InboxArtifact.builder()
              .type(InboxArtifactType.OUTLOOK_EMAIL)
              .externalId(snapshot.getOutlookMessageId())
              .textValue(snapshot.getOutlookAttachmentId())
              .fileName(snapshot.getOutlookAttachmentName())
              .build());
    }
  }

  private void enqueueTranslationIfNeeded(InboxItem item, LegacyPendingKey key) {
    if (item.getOrigin() == ExpenseSource.OUTLOOK_EMAIL
        && StringUtils.isNotBlank(outlookMessageId(item))
        && StringUtils.isBlank(item.getImmutableSourceId())) {
      ensureJob(item, key, BackgroundJobType.TRANSLATE_OUTLOOK_ID, null, false);
    }
  }

  private void resetJob(
      InboxItem item, LegacyPendingKey key, BackgroundJobType type, Integer targetYear) {
    ensureJob(item, key, type, targetYear, true);
  }

  private void ensureJob(
      InboxItem item,
      LegacyPendingKey key,
      BackgroundJobType type,
      Integer targetYear,
      boolean reset) {
    String idempotencyKey = type.name().toLowerCase() + ":" + item.getId();
    BackgroundJob job =
        backgroundJobStore
            .findByIdempotencyKey(idempotencyKey)
            .orElseGet(
                () ->
                    BackgroundJob.builder()
                        .inboxItem(item)
                        .type(type)
                        .idempotencyKey(idempotencyKey)
                        .state(BackgroundJobState.AVAILABLE)
                        .attempts(0)
                        .maxAttempts(DEFAULT_MAX_ATTEMPTS)
                        .availableAt(LocalDateTime.now(clock))
                        .legacyPendingTable(key.getTable())
                        .legacyPendingId(key.getId())
                        .targetYear(targetYear)
                        .build());
    if (job.getId() != null) {
      job = backgroundJobStore.findForUpdate(job.getId()).orElseThrow();
    }
    if (reset && job.getState() != BackgroundJobState.LEASED) {
      BackgroundJobService.resetGeneration(job, LocalDateTime.now(clock));
    }
    job.setLegacyPendingTable(key.getTable());
    job.setLegacyPendingId(key.getId());
    job.setTargetYear(targetYear);
    backgroundJobStore.save(job);
  }

  private BackgroundJobType parseJobType(LegacyInboxSnapshot snapshot) {
    return snapshot.getOrigin() == ExpenseSource.OUTLOOK_EMAIL
        ? BackgroundJobType.PARSE_OUTLOOK
        : BackgroundJobType.PARSE_RECEIPT;
  }

  private ExpenseSource normalizedOrigin(ExpenseSource source) {
    return source == null ? ExpenseSource.MANUAL : source;
  }

  private String outlookMessageId(InboxItem item) {
    return item.getArtifacts().stream()
        .filter(artifact -> artifact.getType() == InboxArtifactType.OUTLOOK_EMAIL)
        .map(InboxArtifact::getExternalId)
        .filter(StringUtils::isNotBlank)
        .findFirst()
        .orElse(item.getLegacySourceId());
  }
}
