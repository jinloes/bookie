package com.bookie.intake.sources;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.InboxCommands;
import com.bookie.intake.application.InboxItemQueued;
import com.bookie.intake.application.InboxItemSaved;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.application.SaveInboxItemCommand;
import com.bookie.intake.application.VenmoImportCommand;
import com.bookie.intake.application.VenmoImportResult;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.InboxStateMachine;
import com.bookie.ledger.application.LedgerTransactionInput;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.domain.ExpenseSource;
import com.bookie.ledger.domain.FinancialTransaction;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Writes inbox decisions to the unified ledger and durable inbox. A save commits the ledger
 * transaction, the SAVED inbox state and any external-sync job together; the job is kicked only
 * after that commit ({@link InboxItemSaved}), and the relay recovers a lost kick.
 */
@Service
@RequiredArgsConstructor
public class InboxCommandService implements InboxCommands {

  private static final Set<InboxState> DISMISSIBLE =
      EnumSet.of(
          InboxState.RECEIVED,
          InboxState.QUEUED,
          InboxState.PROCESSING,
          InboxState.READY,
          InboxState.FAILED);
  private static final Set<InboxState> REPARSEABLE =
      EnumSet.of(InboxState.READY, InboxState.FAILED);
  private static final Set<ExpenseSource> PARSED_ORIGINS =
      EnumSet.of(ExpenseSource.OUTLOOK_EMAIL, ExpenseSource.RECEIPT);
  private static final String ONEDRIVE = "ONEDRIVE";

  private final InboxItemStore inboxItemStore;
  private final BackgroundJobService backgroundJobService;
  private final LedgerTransactionService ledgerTransactionService;
  private final CounterpartyCatalog counterpartyCatalog;
  private final OutlookService outlookService;
  private final InboxItemCreationService inboxItemCreationService;
  private final VenmoImportService venmoImportService;
  private final ApplicationEventPublisher events;
  private final PlatformTransactionManager transactionManager;

  @Override
  public InboxItem save(Long inboxItemId, SaveInboxItemCommand command) {
    return transactionally(() -> saveInTransaction(inboxItemId, command));
  }

  @Override
  public void dismiss(Long inboxItemId) {
    transactionally(
        () -> {
          InboxItem item = require(inboxItemId);
          if (!DISMISSIBLE.contains(item.getState())) {
            throw conflict("Inbox item cannot be dismissed while " + item.getState());
          }
          InboxStateMachine.transition(item, InboxState.DISMISSED);
          boolean outlook = item.getOrigin() == ExpenseSource.OUTLOOK_EMAIL;
          if (outlook) {
            item.setExternalSyncState(ExternalSyncState.PENDING);
          }
          InboxItem dismissed = inboxItemStore.save(item);
          backgroundJobService.terminalizeActive(dismissed.getId(), "DISMISSED");
          if (outlook) {
            scheduleTranslationIfNeeded(dismissed);
            backgroundJobService.schedule(dismissed, BackgroundJobType.MOVE_OUTLOOK, null);
          }
          return dismissed;
        });
  }

  @Override
  public InboxItem reparse(Long inboxItemId) {
    return transactionally(
        () -> {
          InboxItem item = require(inboxItemId);
          if (!PARSED_ORIGINS.contains(item.getOrigin())
              || !REPARSEABLE.contains(item.getState())) {
            throw conflict("Inbox item cannot be reparsed while " + item.getState());
          }
          InboxItem requeued = inboxItemCreationService.requeueForRetry(item);
          events.publishEvent(new InboxItemQueued(requeued.getId()));
          return requeued;
        });
  }

  @Override
  public VenmoImportResult importVenmo(VenmoImportCommand command) {
    return venmoImportService.importStatement(command);
  }

  private InboxItem saveInTransaction(Long inboxItemId, SaveInboxItemCommand command) {
    InboxItem item = require(inboxItemId);
    if (item.getState() != InboxState.READY) {
      throw conflict("Inbox item cannot be saved while " + item.getState());
    }
    if (item.getOrigin() == ExpenseSource.VENMO
        && command.direction() != TransactionDirection.INCOME) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Imported income can only be saved as income");
    }
    outlookService.validateEmailAutoMove(item.getOrigin());
    boolean income = command.direction() == TransactionDirection.INCOME;
    boolean imported = item.getOrigin() == ExpenseSource.VENMO;
    // Imported income already carries the sender and label resolved at import; the review keeps
    // them unless the user picks others.
    Long counterpartyId =
        command.counterpartyId() != null || !imported
            ? command.counterpartyId()
            : item.getLegacyCounterpartyId();
    String sourceLabel =
        command.sourceLabel() != null || !imported
            ? command.sourceLabel()
            : item.getProposedSourceLabel();
    InboxArtifact outlookSource = artifact(item, InboxArtifactType.OUTLOOK_EMAIL).orElse(null);
    boolean attached = StringUtils.isNotBlank(item.getReceiptExternalId());
    FinancialTransaction transaction =
        ledgerTransactionService.createFromInbox(
            LedgerTransactionInput.builder()
                .amount(command.amount())
                .direction(command.direction())
                .date(command.date())
                .description(command.description())
                .activityId(command.activityId())
                .neutralCategoryId(command.neutralCategoryId())
                .counterpartyId(counterpartyId)
                .origin(item.getOrigin().name())
                .externalId(item.getLegacySourceId())
                .sourceLabel(income ? sourceLabel : null)
                .parentExternalId(outlookSource == null ? null : outlookSource.getExternalId())
                .attachmentStorageProvider(attached ? ONEDRIVE : null)
                .attachmentExternalId(attached ? item.getReceiptExternalId() : null)
                .attachmentFileName(attached ? item.getReceiptFileName() : null)
                .build());
    if (!income) {
      learnCounterpartyAliases(item, counterpartyId);
    }

    item.setProposedDirection(command.direction());
    item.setProposedAmount(transaction.getAmount());
    item.setProposedDescription(transaction.getDescription());
    item.setProposedDate(transaction.getDate());
    item.setLegacyActivityId(transaction.getActivity().getId());
    if (income) {
      item.setProposedSourceLabel(sourceLabel);
    }
    if (imported) {
      item.setLegacyCounterpartyId(counterpartyId);
      item.setLegacyPropertyId(
          Optional.ofNullable(transaction.getActivity().getProperty())
              .map(property -> property.getId())
              .orElse(null));
    }
    item.setRawStatus(InboxState.SAVED.name());
    item.setFinancialTransactionId(transaction.getId());
    InboxStateMachine.transition(item, InboxState.SAVE_PENDING);
    InboxStateMachine.transition(item, InboxState.SAVED);
    Optional<BackgroundJobType> externalSync = externalSyncJob(item.getOrigin());
    item.setExternalSyncState(
        externalSync.isPresent() ? ExternalSyncState.PENDING : ExternalSyncState.NOT_REQUIRED);
    InboxItem saved = inboxItemStore.save(item);
    if (saved.getOrigin() == ExpenseSource.OUTLOOK_EMAIL) {
      scheduleTranslationIfNeeded(saved);
    }
    externalSync.ifPresent(
        type -> {
          backgroundJobService.schedule(
              saved,
              type,
              type == BackgroundJobType.MOVE_RECEIPT ? command.date().getYear() : null);
          events.publishEvent(new InboxItemSaved(saved.getId()));
        });
    return saved;
  }

  // An unrecognized alias seen while parsing belongs to the extracted counterparty; confirming a
  // different stored counterparty teaches it the extracted name so future items resolve directly.
  private void learnCounterpartyAliases(InboxItem item, Long counterpartyId) {
    String extractedName = item.getProposedCounterpartyName();
    if (StringUtils.isBlank(extractedName)) {
      return;
    }
    CollectionUtils.emptyIfNull(item.getArtifacts()).stream()
        .filter(artifact -> artifact.getType() == InboxArtifactType.UNRECOGNIZED_ALIAS)
        .map(InboxArtifact::getTextValue)
        .forEach(alias -> counterpartyCatalog.addAliasIfAbsent(extractedName, alias));
    Optional.ofNullable(counterpartyId)
        .flatMap(counterpartyCatalog::findOptionalById)
        .map(Counterparty::getName)
        .filter(name -> !name.equalsIgnoreCase(extractedName))
        .ifPresent(name -> counterpartyCatalog.addAliasIfAbsent(name, extractedName));
  }

  private void scheduleTranslationIfNeeded(InboxItem item) {
    if (StringUtils.isBlank(item.getImmutableSourceId())) {
      backgroundJobService.scheduleIfAbsent(item, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
    }
  }

  private static Optional<BackgroundJobType> externalSyncJob(ExpenseSource origin) {
    if (origin == ExpenseSource.OUTLOOK_EMAIL) {
      return Optional.of(BackgroundJobType.MOVE_OUTLOOK);
    }
    if (origin == ExpenseSource.RECEIPT) {
      return Optional.of(BackgroundJobType.MOVE_RECEIPT);
    }
    return Optional.empty();
  }

  private static Optional<InboxArtifact> artifact(InboxItem item, InboxArtifactType type) {
    return item.getArtifacts().stream().filter(artifact -> artifact.getType() == type).findFirst();
  }

  private InboxItem require(Long inboxItemId) {
    return inboxItemStore
        .findById(inboxItemId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Inbox item not found: " + inboxItemId));
  }

  private InboxItem transactionally(java.util.function.Supplier<InboxItem> work) {
    return new TransactionTemplate(transactionManager).execute(status -> work.get());
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }
}
