package com.bookie.intake.sources;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.financialcategory.application.FinancialCategoryService;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.application.VenmoImportCommand;
import com.bookie.intake.application.VenmoImportResult;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.InboxStateMachine;
import com.bookie.integrations.venmo.VenmoInputException;
import com.bookie.integrations.venmo.VenmoInputPort;
import com.bookie.integrations.venmo.VenmoStatement;
import com.bookie.integrations.venmo.VenmoTransaction;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.ExpenseSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Imports a Venmo statement as READY inbox income awaiting review. */
@Slf4j
@Service
@RequiredArgsConstructor
public class VenmoImportService {

  private static final String DEFAULT_FILE_NAME = "venmo-statement.csv";

  private final Clock clock;
  private final CounterpartyCatalog counterpartyCatalog;
  private final PropertyCatalog propertyCatalog;
  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryService financialCategoryService;
  private final ClassificationHistory classificationHistory;
  private final VenmoInputPort venmoInput;
  private final ReceiptService receiptService;
  private final InboxItemStore inboxItemStore;
  private final LedgerImportReferenceQuery ledgerImportReferenceQuery;
  private final ReceiptHashRepository receiptHashRepository;

  @Transactional
  public VenmoImportResult importStatement(VenmoImportCommand command) {
    Counterparty selectedPayer =
        Optional.ofNullable(command.counterpartyId())
            .map(counterpartyCatalog::findById)
            .orElse(null);
    String senderFilter = selectedPayer != null ? selectedPayer.getName() : null;
    Property requestedProperty =
        Optional.ofNullable(command.propertyId()).map(propertyCatalog::findById).orElse(null);
    FinancialActivity selectedActivity =
        command.activityId() == null
            ? null
            : activityCatalog.resolveForTransaction(
                command.activityId(), requestedProperty == null ? null : requestedProperty.getId());
    Property selectedProperty =
        selectedActivity != null ? selectedActivity.getProperty() : requestedProperty;
    if (selectedActivity == null && selectedProperty == null && selectedPayer != null) {
      selectedProperty = autoDetectPropertyForPayer(selectedPayer);
    }
    VenmoStatement statement;
    try {
      statement = venmoInput.parse(command.content());
    } catch (VenmoInputException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    int importedRows = 0;
    int skippedSenderRows = 0;
    int skippedOutgoingRows = 0;
    int skippedDuplicateRows = 0;
    int skippedInvalidRows = statement.invalidRows();
    Set<Integer> importedYears = new TreeSet<>();
    // Archived lazily: a statement that imports nothing must not reach the receipts pending folder,
    // where auto-import would queue it as a receipt.
    StatementArchive archive = null;

    int nextRowIndex = 0;
    for (VenmoTransaction transaction : statement.transactions()) {
      int rowIndex = nextRowIndex++;
      try {
        if (transaction.amount().signum() <= 0) {
          skippedOutgoingRows++;
          continue;
        }
        if (selectedPayer != null && !matchesSelectedPayer(transaction.sender(), selectedPayer)) {
          skippedSenderRows++;
          continue;
        }
        if (StringUtils.isBlank(transaction.sourceId()) || transaction.date() == null) {
          skippedInvalidRows++;
          continue;
        }
        Optional<InboxItem> existing =
            inboxItemStore.findBySourceIdentity(ExpenseSource.VENMO, transaction.sourceId());
        if (ledgerImportReferenceQuery.existsActive(
                ExpenseSource.VENMO.name(), transaction.sourceId())
            || existing.filter(item -> item.getState() != InboxState.DISMISSED).isPresent()) {
          skippedDuplicateRows++;
          continue;
        }

        // Without a payer filter, resolve the row's sender to a known payer so the property can
        // be detected from that payer's history.
        Counterparty rowPayer =
            selectedPayer != null ? selectedPayer : resolvePayerBySender(transaction.sender());
        Property rowProperty =
            selectedActivity != null
                ? selectedActivity.getProperty()
                : selectedProperty != null
                    ? selectedProperty
                    : (rowPayer != null ? autoDetectPropertyForPayer(rowPayer) : null);
        FinancialActivity rowActivity =
            selectedActivity != null
                ? selectedActivity
                : activityCatalog.resolveForProperty(rowProperty);
        InboxItem item =
            existing.orElseGet(
                () ->
                    InboxItem.builder()
                        .origin(ExpenseSource.VENMO)
                        .state(InboxState.RECEIVED)
                        .externalSyncState(ExternalSyncState.NOT_REQUIRED)
                        .build());
        if (item.getState() == InboxState.DISMISSED) {
          InboxStateMachine.transition(item, InboxState.RECEIVED);
        }
        item.setLegacySourceType(ExpenseSource.VENMO.name());
        item.setLegacySourceId(transaction.sourceId());
        item.setRawStatus(InboxState.READY.name());
        item.setProposedDirection(TransactionDirection.INCOME);
        item.setProposedAmount(transaction.amount());
        item.setProposedDescription(transaction.description());
        item.setProposedDate(transaction.date());
        item.setProposedSourceLabel(
            rowPayer != null
                ? rowPayer.getName()
                : StringUtils.defaultIfBlank(transaction.sender(), "Venmo"));
        item.setLegacyActivityId(rowActivity.getId());
        item.setLegacyCategoryId(
            financialCategoryService
                .defaultFor(rowActivity, TransactionDirection.INCOME, transaction.date())
                .getId());
        item.setLegacyPropertyId(rowProperty == null ? null : rowProperty.getId());
        item.setLegacyCounterpartyId(rowPayer == null ? null : rowPayer.getId());
        item.setClassificationAmbiguous(
            ActivityCatalog.NEEDS_CLASSIFICATION_KEY.equals(rowActivity.getSystemKey()));
        item.setErrorMessage(null);
        if (archive == null) {
          archive = archiveStatement(command.content(), command.fileName());
        }
        item.setReceiptExternalId(archive.oneDriveId());
        item.setReceiptFileName(archive.fileName());
        item.setCreatedAt(LocalDateTime.now(clock));
        item.removeArtifacts(InboxArtifactType.RECEIPT);
        if (StringUtils.isNotBlank(archive.oneDriveId())) {
          item.addArtifact(
              InboxArtifact.builder()
                  .type(InboxArtifactType.RECEIPT)
                  .externalId(archive.oneDriveId())
                  .fileName(archive.fileName())
                  .sha256(
                      receiptHashRepository
                          .findByDriveItemId(archive.oneDriveId())
                          .map(ReceiptHash::getSha256)
                          .orElse(null))
                  .build());
        }
        InboxStateMachine.transition(item, InboxState.READY);
        inboxItemStore.save(item);
        importedRows++;
        importedYears.add(transaction.date().getYear());
      } catch (StatementArchiveFailure failure) {
        throw failure.cause();
      } catch (RuntimeException ex) {
        skippedInvalidRows++;
        // Index and exception type only: the row's sender, amount and source ID must not be logged.
        log.warn(
            "Venmo statement import row {} could not be proposed: {}",
            rowIndex,
            ex.getClass().getSimpleName());
      }
    }

    if (archive != null) {
      moveArchivedStatementToTaxYear(archive, importedYears);
    }

    return VenmoImportResult.builder()
        .totalRows(statement.totalRows())
        .importedRows(importedRows)
        .skippedSenderRows(skippedSenderRows)
        .skippedOutgoingRows(skippedOutgoingRows)
        .skippedDuplicateRows(skippedDuplicateRows)
        .skippedInvalidRows(skippedInvalidRows)
        .senderFilter(senderFilter)
        .propertyName(selectedProperty != null ? selectedProperty.getName() : null)
        .activityName(selectedActivity != null ? selectedActivity.getName() : null)
        .build();
  }

  private boolean matchesSelectedPayer(String sender, Counterparty selectedPayer) {
    if (StringUtils.isBlank(sender)) {
      return false;
    }
    String canonicalSender = canonicalCounterparty(sender);
    if (canonicalCounterparty(selectedPayer.getName()).equals(canonicalSender)) {
      return true;
    }
    boolean aliasMatch =
        CollectionUtils.emptyIfNull(selectedPayer.getAliases()).stream()
            .map(this::canonicalCounterparty)
            .anyMatch(canonicalSender::equals);
    if (aliasMatch) {
      return true;
    }
    return CollectionUtils.emptyIfNull(selectedPayer.getAccounts()).stream()
        .map(this::canonicalCounterparty)
        .anyMatch(canonicalSender::equals);
  }

  private String canonicalCounterparty(String value) {
    String normalized = StringUtils.defaultString(value).trim().toLowerCase();
    return normalized.startsWith("@") ? normalized.substring(1) : normalized;
  }

  private Counterparty resolvePayerBySender(String sender) {
    String trimmed = StringUtils.trimToNull(sender);
    if (trimmed == null) {
      return null;
    }
    String normalized = StringUtils.removeStart(trimmed, "@");
    return counterpartyCatalog
        .findByName(normalized)
        .or(() -> counterpartyCatalog.findByAlias(normalized))
        .orElse(null);
  }

  private Property autoDetectPropertyForPayer(Counterparty payer) {
    return classificationHistory.findMostLikelyPropertyForCounterparty(payer.getId()).orElse(null);
  }

  private StatementArchive archiveStatement(byte[] content, String originalFilename) {
    try {
      return uploadStatement(content, originalFilename);
    } catch (RuntimeException failure) {
      // An upload failure fails the whole import rather than counting a row as invalid.
      throw new StatementArchiveFailure(failure);
    }
  }

  private StatementArchive uploadStatement(byte[] content, String originalFilename) {
    if (!receiptService.isConnected()) {
      return StatementArchive.EMPTY;
    }
    String filename =
        StringUtils.defaultIfBlank(StringUtils.trimToNull(originalFilename), DEFAULT_FILE_NAME);
    UploadReceiptResponse uploaded = receiptService.uploadReceipt(filename, content);
    if (uploaded == null
        || uploaded.receipt() == null
        || StringUtils.isBlank(uploaded.receipt().id())) {
      log.warn(
          "Venmo statement upload returned no OneDrive ID; incomes will be saved without"
              + " attachment");
      return StatementArchive.EMPTY;
    }
    return new StatementArchive(uploaded.receipt().id(), uploaded.receipt().name());
  }

  private void moveArchivedStatementToTaxYear(StatementArchive archive, Set<Integer> years) {
    if (StringUtils.isBlank(archive.oneDriveId()) || years.isEmpty()) {
      return;
    }
    receiptService.moveTaxesFolder(archive.oneDriveId(), years.iterator().next());
  }

  private record StatementArchive(String oneDriveId, String fileName) {
    private static final StatementArchive EMPTY = new StatementArchive(null, null);
  }

  private static final class StatementArchiveFailure extends RuntimeException {
    private StatementArchiveFailure(RuntimeException cause) {
      super(cause);
    }

    private RuntimeException cause() {
      return (RuntimeException) getCause();
    }
  }
}
