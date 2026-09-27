package com.bookie.intake.sources;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.financialcategory.application.FinancialCategoryService;
import com.bookie.catalog.property.domain.Property;
import com.bookie.intake.application.BankImportCommand;
import com.bookie.intake.application.BankImportResult;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.InboxStateMachine;
import com.bookie.integrations.bank.BankInputException;
import com.bookie.integrations.bank.BankStatement;
import com.bookie.integrations.bank.BankStatementInputPort;
import com.bookie.integrations.bank.BankTransaction;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.ExpenseSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Imports the deposits of an OFX/QFX bank statement as READY inbox income awaiting review. The
 * uploaded file is never stored or logged; only the extracted transaction fields are kept.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankImportService {

  private static final String DEFAULT_SOURCE_LABEL = "Bank";

  private final Clock clock;
  private final CounterpartyCatalog counterpartyCatalog;
  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryService financialCategoryService;
  private final ClassificationHistory classificationHistory;
  private final BankStatementInputPort bankStatementInput;
  private final InboxItemStore inboxItemStore;
  private final LedgerImportReferenceQuery ledgerImportReferenceQuery;

  @Transactional
  public BankImportResult importStatement(BankImportCommand command) {
    FinancialActivity selectedActivity =
        command.activityId() == null
            ? null
            : activityCatalog.resolveForTransaction(command.activityId(), null);
    BankStatement statement;
    try {
      statement = bankStatementInput.parse(command.content());
    } catch (BankInputException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    int importedRows = 0;
    int skippedDebitRows = 0;
    int skippedDuplicateRows = 0;
    int skippedInvalidRows = statement.invalidRows();

    for (BankTransaction transaction : statement.transactions()) {
      try {
        if (transaction.amount().signum() <= 0) {
          skippedDebitRows++;
          continue;
        }
        Optional<InboxItem> existing =
            inboxItemStore.findBySourceIdentity(ExpenseSource.BANK, transaction.sourceId());
        if (ledgerImportReferenceQuery.existsActive(
                ExpenseSource.BANK.name(), transaction.sourceId())
            || existing.filter(item -> item.getState() != InboxState.DISMISSED).isPresent()) {
          skippedDuplicateRows++;
          continue;
        }
        propose(existing, transaction, selectedActivity);
        importedRows++;
      } catch (RuntimeException ex) {
        skippedInvalidRows++;
      }
    }

    log.info(
        "Bank statement import: {} rows, {} imported, {} debits, {} duplicates, {} invalid",
        statement.totalRows(),
        importedRows,
        skippedDebitRows,
        skippedDuplicateRows,
        skippedInvalidRows);
    return BankImportResult.builder()
        .totalRows(statement.totalRows())
        .importedRows(importedRows)
        .skippedDebitRows(skippedDebitRows)
        .skippedDuplicateRows(skippedDuplicateRows)
        .skippedInvalidRows(skippedInvalidRows)
        .activityName(selectedActivity != null ? selectedActivity.getName() : null)
        .build();
  }

  private void propose(
      Optional<InboxItem> existing,
      BankTransaction transaction,
      FinancialActivity selectedActivity) {
    Counterparty payer = resolvePayer(transaction.name());
    Property property =
        selectedActivity != null
            ? selectedActivity.getProperty()
            : Optional.ofNullable(payer)
                .flatMap(
                    found ->
                        classificationHistory.findMostLikelyPropertyForCounterparty(found.getId()))
                .orElse(null);
    FinancialActivity activity =
        selectedActivity != null ? selectedActivity : activityCatalog.resolveForProperty(property);
    InboxItem item =
        existing.orElseGet(
            () ->
                InboxItem.builder()
                    .origin(ExpenseSource.BANK)
                    .state(InboxState.RECEIVED)
                    .externalSyncState(ExternalSyncState.NOT_REQUIRED)
                    .build());
    if (item.getState() == InboxState.DISMISSED) {
      InboxStateMachine.transition(item, InboxState.RECEIVED);
    }
    item.setLegacySourceType(ExpenseSource.BANK.name());
    item.setLegacySourceId(transaction.sourceId());
    item.setRawStatus(InboxState.READY.name());
    item.setProposedDirection(TransactionDirection.INCOME);
    item.setProposedAmount(transaction.amount());
    item.setProposedDescription(describe(transaction));
    item.setProposedDate(transaction.date());
    item.setProposedSourceLabel(
        payer != null
            ? payer.getName()
            : StringUtils.defaultIfBlank(transaction.name(), DEFAULT_SOURCE_LABEL));
    item.setLegacyActivityId(activity.getId());
    item.setLegacyCategoryId(
        financialCategoryService
            .defaultFor(activity, TransactionDirection.INCOME, transaction.date())
            .getId());
    item.setLegacyPropertyId(property == null ? null : property.getId());
    item.setLegacyCounterpartyId(payer == null ? null : payer.getId());
    item.setClassificationAmbiguous(
        ActivityCatalog.NEEDS_CLASSIFICATION_KEY.equals(activity.getSystemKey()));
    item.setErrorMessage(null);
    item.setCreatedAt(LocalDateTime.now(clock));
    InboxStateMachine.transition(item, InboxState.READY);
    inboxItemStore.save(item);
  }

  private static String describe(BankTransaction transaction) {
    String name = StringUtils.defaultIfBlank(transaction.name(), DEFAULT_SOURCE_LABEL);
    String memo = transaction.memo();
    return StringUtils.isNotBlank(memo) && !memo.equalsIgnoreCase(name)
        ? name + " - " + memo
        : name;
  }

  private Counterparty resolvePayer(String name) {
    String trimmed = StringUtils.trimToNull(name);
    if (trimmed == null) {
      return null;
    }
    return counterpartyCatalog
        .findByName(trimmed)
        .or(() -> counterpartyCatalog.findByAlias(trimmed))
        .orElse(null);
  }
}
