package com.bookie.intake.sources;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.application.FinancialCategoryService;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.InboxItemQueued;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.InboxStateMachine;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.ExpenseSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates, requeues and completes parsed inbox items: Outlook messages or attachments and receipts
 * that must be parsed before review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxItemCreationService {

  private static final String PROCESSING = "PROCESSING";
  private static final Set<InboxState> IN_FLIGHT =
      EnumSet.of(
          InboxState.RECEIVED, InboxState.QUEUED, InboxState.PROCESSING, InboxState.SAVE_PENDING);

  private final Clock clock;
  private final InboxItemStore inboxItemStore;
  private final BackgroundJobService backgroundJobService;
  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryService financialCategoryService;
  private final LedgerImportReferenceQuery ledgerImportReferenceQuery;
  private final ReceiptHashRepository receiptHashRepository;
  private final PlatformTransactionManager transactionManager;
  private final ApplicationEventPublisher events;

  /** Return type for {@link #findOrCreate}. */
  public record FindOrCreateResult(InboxItem item, boolean alreadyProcessing) {}

  /** Source fields recorded for a parsed intake item. */
  public record IntakeSource(
      String sourceId,
      ExpenseSource origin,
      String subject,
      Long configuredActivityId,
      String outlookMessageId,
      String outlookAttachmentId,
      String outlookAttachmentName) {

    public static IntakeSource of(String sourceId, ExpenseSource origin, String subject) {
      return new IntakeSource(sourceId, origin, subject, null, null, null, null);
    }
  }

  /**
   * Returns the existing item unchanged while it is still being parsed; otherwise resets a stale
   * item in place and queues it again. New items are created in a separate transaction so a
   * unique-identity conflict from a concurrent request can roll back without invalidating this
   * method's persistence context; the concurrently inserted item is then returned.
   *
   * <p>Rejects the request outright once this source has been saved to the ledger, so a stale
   * "Parse" click, retry or duplicate trigger cannot create an item that can never be saved.
   */
  @Transactional
  public FindOrCreateResult findOrCreate(IntakeSource source) {
    if (ledgerImportReferenceQuery.existsActive(source.origin().name(), source.sourceId())) {
      throw alreadySaved();
    }
    Optional<InboxItem> existing =
        inboxItemStore.findBySourceIdentity(source.origin(), source.sourceId());
    if (existing.isPresent()) {
      InboxItem item = existing.get();
      if (item.getState() == InboxState.SAVED) {
        throw alreadySaved();
      }
      if (IN_FLIGHT.contains(item.getState())) {
        return new FindOrCreateResult(item, true);
      }
      return new FindOrCreateResult(requeue(item, source), false);
    }
    TransactionTemplate isolated = new TransactionTemplate(transactionManager);
    isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    try {
      return new FindOrCreateResult(isolated.execute(status -> create(source)), false);
    } catch (DataIntegrityViolationException conflict) {
      return inboxItemStore
          .findBySourceIdentity(source.origin(), source.sourceId())
          .map(item -> new FindOrCreateResult(item, true))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "findOrCreate conflict but no inbox item found for sourceId="
                          + source.sourceId(),
                      conflict));
    }
  }

  /** Records a parse result as the item's reviewable proposal. */
  @Transactional
  public void markReady(Long inboxItemId, EmailSuggestion suggestion, List<String> aliases) {
    InboxItem item =
        inboxItemStore
            .findById(inboxItemId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inbox item not found: " + inboxItemId));
    backgroundJobService.requireCurrentParse(inboxItemId);
    LocalDate date = suggestion.date() != null ? LocalDate.parse(suggestion.date()) : null;
    FinancialActivity activity =
        suggestion.activityId() != null
            ? activityCatalog.findActiveById(suggestion.activityId())
            : activityCatalog.resolveForSuggestedProperty(suggestion.propertyName());
    TransactionDirection categoryDirection =
        suggestion.emailType() == EmailType.INCOME
            ? TransactionDirection.INCOME
            : TransactionDirection.EXPENSE;
    FinancialCategory category;
    boolean classificationAmbiguous = suggestion.classificationAmbiguous();
    try {
      category =
          financialCategoryService.resolve(
              suggestion.categoryId(),
              suggestion.categoryId() == null ? suggestion.category() : null,
              categoryDirection,
              activity,
              date);
    } catch (ResponseStatusException incompatibleSuggestion) {
      category = financialCategoryService.defaultFor(activity, categoryDirection, date);
      classificationAmbiguous = true;
    }
    item.setProposedDirection(suggestion.emailType() == null ? null : categoryDirection);
    item.setProposedAmount(
        suggestion.amount() != null ? BigDecimal.valueOf(suggestion.amount()) : null);
    item.setProposedDescription(suggestion.description());
    item.setProposedDate(date);
    item.setProposedCategory(suggestion.category());
    item.setProposedPropertyName(suggestion.propertyName());
    item.setProposedCounterpartyName(suggestion.payerName());
    item.setLegacyActivityId(activity.getId());
    item.setLegacyCategoryId(category.getId());
    item.setClassificationAmbiguous(classificationAmbiguous);
    item.setRawStatus(InboxState.READY.name());
    CollectionUtils.emptyIfNull(aliases).forEach(alias -> item.addArtifact(alias(alias)));
    InboxStateMachine.transition(item, InboxState.READY);
    inboxItemStore.save(item);
  }

  /** Records a parse failure so the item can be retried or dismissed. */
  @Transactional
  public void markFailed(Long inboxItemId, String error) {
    inboxItemStore
        .findById(inboxItemId)
        .ifPresentOrElse(
            item -> {
              backgroundJobService.requireCurrentParse(inboxItemId);
              item.setRawStatus(InboxState.FAILED.name());
              item.setErrorMessage(error);
              InboxStateMachine.transition(item, InboxState.FAILED);
              inboxItemStore.save(item);
            },
            () -> log.warn("Could not mark inbox item {} as failed: not found", inboxItemId));
  }

  /** Queues a READY or FAILED item for another parse, keeping its current proposal. */
  @Transactional
  public InboxItem requeueForRetry(InboxItem item) {
    if (PROCESSING.equals(item.getRawStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Already processing");
    }
    item.setRawStatus(PROCESSING);
    item.setErrorMessage(null);
    InboxStateMachine.transition(item, InboxState.QUEUED);
    InboxItem saved = inboxItemStore.save(item);
    backgroundJobService.schedule(saved, parseJobType(saved.getOrigin()), null);
    return saved;
  }

  private InboxItem create(IntakeSource source) {
    InboxItem item =
        InboxItem.builder()
            .origin(source.origin())
            .state(InboxState.RECEIVED)
            .externalSyncState(ExternalSyncState.NOT_REQUIRED)
            .createdAt(LocalDateTime.now(clock))
            .build();
    applySource(item, source);
    InboxStateMachine.transition(item, InboxState.QUEUED);
    return queueParse(inboxItemStore.save(item));
  }

  private InboxItem requeue(InboxItem item, IntakeSource source) {
    if (item.getState() == InboxState.DISMISSED) {
      // A dismissed source that arrives again is a new intake event.
      item.setCreatedAt(LocalDateTime.now(clock));
    }
    applySource(item, source);
    InboxStateMachine.transition(item, InboxState.QUEUED);
    return queueParse(inboxItemStore.save(item));
  }

  private InboxItem queueParse(InboxItem item) {
    backgroundJobService.schedule(item, parseJobType(item.getOrigin()), null);
    if (item.getOrigin() == ExpenseSource.OUTLOOK_EMAIL
        && StringUtils.isNotBlank(outlookMessageId(item))
        && StringUtils.isBlank(item.getImmutableSourceId())) {
      backgroundJobService.scheduleIfAbsent(item, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
    }
    events.publishEvent(new InboxItemQueued(item.getId()));
    return item;
  }

  private void applySource(InboxItem item, IntakeSource source) {
    FinancialActivity activity =
        source.configuredActivityId() == null
            ? activityCatalog.getNeedsClassification()
            : activityCatalog.findActiveById(source.configuredActivityId());
    boolean receipt = source.origin() == ExpenseSource.RECEIPT;
    item.setOrigin(source.origin());
    item.setLegacySourceType(source.origin().name());
    item.setLegacySourceId(source.sourceId());
    item.setRawStatus(PROCESSING);
    item.setSubject(source.subject());
    item.setProposedDirection(null);
    item.setProposedAmount(null);
    item.setProposedDescription(null);
    item.setProposedDate(null);
    item.setProposedCategory(null);
    item.setProposedPropertyName(null);
    item.setProposedCounterpartyName(null);
    item.setProposedSourceLabel(null);
    item.setLegacyActivityId(activity.getId());
    item.setLegacyCategoryId(
        financialCategoryService.defaultFor(activity, TransactionDirection.EXPENSE).getId());
    item.setLegacyPropertyId(null);
    item.setLegacyCounterpartyId(null);
    item.setConfiguredActivityId(source.configuredActivityId());
    item.setClassificationAmbiguous(true);
    item.setErrorMessage(null);
    item.setReceiptExternalId(receipt ? source.sourceId() : null);
    item.setReceiptFileName(receipt ? source.subject() : null);
    item.removeArtifacts(InboxArtifactType.UNRECOGNIZED_ALIAS);
    item.removeArtifacts(InboxArtifactType.RECEIPT);
    if (receipt) {
      item.addArtifact(
          InboxArtifact.builder()
              .type(InboxArtifactType.RECEIPT)
              .externalId(source.sourceId())
              .fileName(source.subject())
              .sha256(
                  receiptHashRepository
                      .findByDriveItemId(source.sourceId())
                      .map(ReceiptHash::getSha256)
                      .orElse(null))
              .build());
    }
    item.removeArtifacts(InboxArtifactType.OUTLOOK_EMAIL);
    if (StringUtils.isNotBlank(source.outlookMessageId())) {
      item.addArtifact(
          InboxArtifact.builder()
              .type(InboxArtifactType.OUTLOOK_EMAIL)
              .externalId(source.outlookMessageId())
              .textValue(source.outlookAttachmentId())
              .fileName(source.outlookAttachmentName())
              .build());
    }
  }

  private static String outlookMessageId(InboxItem item) {
    return item.getArtifacts().stream()
        .filter(artifact -> artifact.getType() == InboxArtifactType.OUTLOOK_EMAIL)
        .map(InboxArtifact::getExternalId)
        .filter(StringUtils::isNotBlank)
        .findFirst()
        .orElse(item.getLegacySourceId());
  }

  private static InboxArtifact alias(String alias) {
    return InboxArtifact.builder()
        .type(InboxArtifactType.UNRECOGNIZED_ALIAS)
        .textValue(alias)
        .build();
  }

  private static BackgroundJobType parseJobType(ExpenseSource origin) {
    return origin == ExpenseSource.OUTLOOK_EMAIL
        ? BackgroundJobType.PARSE_OUTLOOK
        : BackgroundJobType.PARSE_RECEIPT;
  }

  private static ResponseStatusException alreadySaved() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "This item has already been saved. Refresh to see the update.");
  }
}
