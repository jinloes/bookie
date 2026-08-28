package com.bookie.service;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.ConfirmedClassification;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
import com.bookie.compatibility.intake.LegacyInboxSnapshots;
import com.bookie.intake.application.LegacyInboxReadSelector;
import com.bookie.intake.application.LegacyInboxSynchronizer;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import com.bookie.integrations.venmo.VenmoInputException;
import com.bookie.integrations.venmo.VenmoInputPort;
import com.bookie.integrations.venmo.VenmoStatement;
import com.bookie.integrations.venmo.VenmoTransaction;
import com.bookie.ledger.application.LedgerReadMode;
import com.bookie.ledger.compatibility.LegacyLedgerReadAdapter;
import com.bookie.ledger.compatibility.LegacyLedgerSynchronizer;
import com.bookie.ledger.compatibility.api.VenmoIncomeImportResponse;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionTable;
import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Income;
import com.bookie.model.PendingIncome;
import com.bookie.model.PendingIncomeStatus;
import com.bookie.model.TransactionDirection;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.model.UploadReceiptResponse;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.PendingIncomeRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class IncomeService {

  private final IncomeRepository incomeRepository;
  private final PropertyCatalog propertyCatalog;
  private final CounterpartyCatalog counterpartyCatalog;
  private final ReceiptService receiptService;
  private final PendingIncomeRepository pendingIncomeRepository;
  private final ClassificationHistory classificationHistory;
  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryService financialCategoryService;
  private final LegacyLedgerSynchronizer ledgerSynchronizer;
  private final LegacyLedgerReadAdapter ledgerReadAdapter;
  private final VenmoInputPort venmoInput;
  private final LegacyInboxSynchronizer inboxSynchronizer;
  private final LegacyInboxReadSelector inboxReadSelector;

  @Value("${bookie.ledger.read-mode:UNIFIED}")
  private LedgerReadMode ledgerReadMode = LedgerReadMode.UNIFIED;

  public List<Income> findAll() {
    if (ledgerReadMode == LedgerReadMode.UNIFIED) {
      return ledgerReadAdapter.findAllIncomes();
    }
    List<Income> incomes = incomeRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
    if (ledgerReadMode == LedgerReadMode.COMPARE) {
      ledgerReadAdapter.assertIncomeParity(incomes);
    }
    return incomes;
  }

  public Income findById(Long id) {
    if (ledgerReadMode == LedgerReadMode.UNIFIED) {
      return ledgerReadAdapter.findIncomeById(id);
    }
    Income income =
        incomeRepository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income not found: " + id));
    if (ledgerReadMode == LedgerReadMode.COMPARE) {
      ledgerReadAdapter.assertIncomeParity(income);
    }
    return income;
  }

  /** Returns whether an income already exists for the given source type and external source ID. */
  public boolean existsBySourceId(ExpenseSource sourceType, String sourceId) {
    return incomeRepository.existsBySourceTypeAndSourceId(sourceType, sourceId);
  }

  @Transactional
  public Income create(CreateIncomeRequest req) {
    FinancialActivity activity =
        activityCatalog.resolveForTransaction(req.activityId(), req.propertyId());
    Property property = activity.getProperty();
    Counterparty payer = req.payerId() != null ? counterpartyCatalog.findById(req.payerId()) : null;
    FinancialCategory category =
        financialCategoryService.resolve(
            req.categoryId(), null, TransactionDirection.INCOME, activity, req.date());
    Income income =
        Income.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .source(req.source())
            .property(property)
            .payer(payer)
            .activity(activity)
            .financialCategory(category)
            .sourceType(ExpenseSource.MANUAL)
            .receiptOneDriveId(req.receiptOneDriveId())
            .receiptFileName(req.receiptFileName())
            .build();
    return save(income);
  }

  @Transactional
  public VenmoIncomeImportResponse importVenmoCsv(byte[] csvBytes, String payer)
      throws IOException {
    return importVenmoCsv(csvBytes, "venmo-statement.csv", payer, null, null);
  }

  @Transactional
  public VenmoIncomeImportResponse importVenmoCsv(
      byte[] csvBytes, String originalFilename, String payer, String propertyIdStr)
      throws IOException {
    return importVenmoCsv(csvBytes, originalFilename, payer, propertyIdStr, null);
  }

  @Transactional
  public VenmoIncomeImportResponse importVenmoCsv(
      byte[] csvBytes,
      String originalFilename,
      String payer,
      String propertyIdStr,
      String activityIdStr)
      throws IOException {
    Counterparty selectedPayer = resolveSelectedPayer(payer);
    String senderFilter = selectedPayer != null ? selectedPayer.getName() : null;
    Property requestedProperty = resolveSelectedProperty(propertyIdStr);
    FinancialActivity selectedActivity = resolveSelectedActivity(activityIdStr, requestedProperty);
    Property selectedProperty =
        selectedActivity != null ? selectedActivity.getProperty() : requestedProperty;
    if (selectedActivity == null && selectedProperty == null && selectedPayer != null) {
      selectedProperty = autoDetectPropertyForPayer(selectedPayer);
    }
    VenmoStatementArchive archive = archiveVenmoStatement(csvBytes, originalFilename);
    VenmoStatement statement;
    try {
      statement = venmoInput.parse(csvBytes);
    } catch (VenmoInputException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    int totalRows = statement.totalRows();
    int importedRows = 0;
    int skippedSenderRows = 0;
    int skippedOutgoingRows = 0;
    int skippedDuplicateRows = 0;
    int skippedInvalidRows = statement.invalidRows();
    Set<Integer> importedYears = new TreeSet<>();

    for (VenmoTransaction transaction : statement.transactions()) {
      try {
        String sourceId = transaction.sourceId();
        String sender = transaction.sender();
        BigDecimal amount = transaction.amount();
        LocalDate date = transaction.date();
        String description = transaction.description();

        if (amount.signum() <= 0) {
          skippedOutgoingRows++;
          continue;
        }

        if (selectedPayer != null && !matchesSelectedPayer(sender, selectedPayer)) {
          skippedSenderRows++;
          continue;
        }

        if (StringUtils.isAnyBlank(sourceId) || date == null) {
          skippedInvalidRows++;
          continue;
        }

        if (incomeRepository.existsBySourceTypeAndSourceId(ExpenseSource.VENMO, sourceId)
            || pendingIncomeRepository.existsBySourceTypeAndSourceId(
                ExpenseSource.VENMO, sourceId)) {
          skippedDuplicateRows++;
          continue;
        }

        // When no payer filter was provided, resolve the row's sender to a known payer
        // so we can auto-detect the property from that payer's history.
        Counterparty rowPayer =
            selectedPayer != null ? selectedPayer : resolvePayerBySender(sender);
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
        FinancialCategory rowCategory =
            financialCategoryService.defaultFor(rowActivity, TransactionDirection.INCOME, date);

        PendingIncome pending =
            pendingIncomeRepository.save(
                PendingIncome.builder()
                    .sourceId(sourceId)
                    .sourceType(ExpenseSource.VENMO)
                    .status(PendingIncomeStatus.READY)
                    .amount(amount)
                    .description(description)
                    .date(date)
                    .source(
                        rowPayer != null
                            ? rowPayer.getName()
                            : StringUtils.defaultIfBlank(sender, "Venmo"))
                    .payer(rowPayer)
                    .property(rowProperty)
                    .activity(rowActivity)
                    .financialCategory(rowCategory)
                    .classificationAmbiguous(
                        ActivityCatalog.NEEDS_CLASSIFICATION_KEY.equals(rowActivity.getSystemKey()))
                    .receiptOneDriveId(archive.oneDriveId())
                    .receiptFileName(archive.fileName())
                    .createdAt(LocalDateTime.now())
                    .build());
        inboxSynchronizer.created(
            pendingKey(pending.getId()), LegacyInboxSnapshots.from(pending), false);
        importedRows++;
        importedYears.add(date.getYear());
      } catch (RuntimeException ex) {
        skippedInvalidRows++;
      }
    }

    moveArchivedStatementToTaxYear(archive, importedYears);

    String propertyName = selectedProperty != null ? selectedProperty.getName() : null;
    String activityName = selectedActivity != null ? selectedActivity.getName() : null;
    return new VenmoIncomeImportResponse(
        totalRows,
        importedRows,
        skippedSenderRows,
        skippedOutgoingRows,
        skippedDuplicateRows,
        skippedInvalidRows,
        senderFilter,
        propertyName,
        activityName);
  }

  @Transactional
  public Income save(Income income) {
    Income saved = incomeRepository.save(income);
    ledgerSynchronizer.synchronize(saved);
    classificationHistory.record(classification(saved));
    return saved;
  }

  @Transactional
  public Income update(Long id, UpdateIncomeRequest req) {
    Income existing = findById(id);
    FinancialActivity activity =
        req.activityId() == null && req.propertyId() == null && existing.getActivity() != null
            ? activityCatalog.findActiveById(existing.getActivity().getId())
            : activityCatalog.resolveForTransaction(req.activityId(), req.propertyId());
    Property property = activity.getProperty();
    Counterparty payer = req.payerId() != null ? counterpartyCatalog.findById(req.payerId()) : null;
    FinancialCategory category =
        req.categoryId() != null
            ? financialCategoryService.resolve(
                req.categoryId(), null, TransactionDirection.INCOME, activity, req.date())
            : compatibleOrDefault(
                existing.getFinancialCategory(), activity, TransactionDirection.INCOME, req.date());
    existing.setAmount(req.amount());
    existing.setDescription(req.description());
    existing.setDate(req.date());
    existing.setSource(req.source());
    existing.setProperty(property);
    existing.setPayer(payer);
    existing.setActivity(activity);
    existing.setFinancialCategory(category);
    Income saved = incomeRepository.save(existing);
    ledgerSynchronizer.synchronize(saved);
    classificationHistory.record(classification(saved));
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    ledgerSynchronizer.tombstoneIncome(id);
    incomeRepository.deleteById(id);
    incomeRepository.flush();
  }

  @Transactional
  public void updateSourceId(Long id, String newSourceId) {
    incomeRepository
        .findById(id)
        .ifPresent(
            income -> {
              income.setSourceId(newSourceId);
              Income saved = incomeRepository.save(income);
              ledgerSynchronizer.synchronize(saved);
            });
  }

  public BigDecimal getTotalIncome() {
    if (ledgerReadMode == LedgerReadMode.UNIFIED) {
      return ledgerReadAdapter.getTotalIncome();
    }
    BigDecimal total = incomeRepository.getTotalIncome();
    if (ledgerReadMode == LedgerReadMode.COMPARE) {
      ledgerReadAdapter.assertIncomeParity(incomeRepository.findAll());
    }
    return total;
  }

  @Transactional(readOnly = true)
  public List<PendingIncome> findAllPending() {
    List<PendingIncome> legacy =
        pendingIncomeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    return inboxReadSelector.select(
        LegacyPendingTable.PENDING_INCOMES,
        legacy,
        PendingIncome::getId,
        LegacyInboxSnapshots::from);
  }

  public PendingIncome findPendingById(Long id) {
    return pendingIncomeRepository
        .findById(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Pending income not found: " + id));
  }

  @Transactional
  public Income acceptPendingIncome(Long id, UpdateIncomeRequest updates) {
    PendingIncome pending = findPendingById(id);
    Long activityId =
        updates.activityId() != null
            ? updates.activityId()
            : (updates.propertyId() == null && pending.getActivity() != null
                ? pending.getActivity().getId()
                : null);
    FinancialActivity activity =
        activityCatalog.resolveForTransaction(activityId, updates.propertyId());
    Property property = activity.getProperty();
    Counterparty payer =
        updates.payerId() != null
            ? counterpartyCatalog.findById(updates.payerId())
            : pending.getPayer();
    LocalDate effectiveOn = updates.date() != null ? updates.date() : pending.getDate();
    FinancialCategory category =
        updates.categoryId() != null
            ? financialCategoryService.resolve(
                updates.categoryId(), null, TransactionDirection.INCOME, activity, effectiveOn)
            : compatibleOrDefault(
                pending.getFinancialCategory(), activity, TransactionDirection.INCOME, effectiveOn);

    pending.setAmount(updates.amount() != null ? updates.amount() : pending.getAmount());
    pending.setDescription(
        updates.description() != null ? updates.description() : pending.getDescription());
    pending.setDate(effectiveOn);
    pending.setSource(updates.source() != null ? updates.source() : pending.getSource());
    pending.setProperty(property);
    pending.setPayer(payer);
    pending.setActivity(activity);
    pending.setFinancialCategory(category);
    inboxSynchronizer.savePending(pendingKey(id), LegacyInboxSnapshots.from(pending));

    Income income =
        Income.builder()
            .amount(pending.getAmount())
            .description(pending.getDescription())
            .date(effectiveOn)
            .source(pending.getSource())
            .sourceId(pending.getSourceId())
            .sourceType(pending.getSourceType())
            .property(property)
            .payer(payer)
            .activity(activity)
            .financialCategory(category)
            .receiptOneDriveId(pending.getReceiptOneDriveId())
            .receiptFileName(pending.getReceiptFileName())
            .build();
    income = save(income);

    inboxSynchronizer.saved(
        pendingKey(id),
        LegacyInboxSnapshots.from(pending),
        new LegacyTransactionKey(LegacyTransactionTable.INCOMES, income.getId()));
    pendingIncomeRepository.deleteById(id);
    return income;
  }

  @Transactional
  public void rejectPendingIncome(Long id) {
    PendingIncome pending = findPendingById(id);
    inboxSynchronizer.dismissed(pendingKey(id), LegacyInboxSnapshots.from(pending));
    pendingIncomeRepository.delete(pending);
  }

  private LegacyPendingKey pendingKey(Long id) {
    return new LegacyPendingKey(LegacyPendingTable.PENDING_INCOMES, id);
  }

  private FinancialCategory compatibleOrDefault(
      FinancialCategory current,
      FinancialActivity activity,
      TransactionDirection direction,
      LocalDate effectiveOn) {
    if (financialCategoryService.isCompatible(current, activity, direction, effectiveOn)) {
      return current;
    }
    return financialCategoryService.defaultFor(activity, direction, effectiveOn);
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

  private Counterparty resolveSelectedPayer(String payer) {
    String trimmed = StringUtils.trimToNull(payer);
    if (trimmed == null) {
      return null;
    }
    if (StringUtils.isNumeric(trimmed)) {
      return counterpartyCatalog.findById(Long.parseLong(trimmed));
    }
    return counterpartyCatalog
        .findByName(trimmed)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unknown payer filter: " + trimmed));
  }

  private Property resolveSelectedProperty(String propertyIdStr) {
    String trimmed = StringUtils.trimToNull(propertyIdStr);
    if (trimmed == null) {
      return null;
    }
    if (StringUtils.isNumeric(trimmed)) {
      return propertyCatalog.findById(Long.parseLong(trimmed));
    }
    return null;
  }

  private FinancialActivity resolveSelectedActivity(
      String activityIdStr, Property requestedProperty) {
    String trimmed = StringUtils.trimToNull(activityIdStr);
    if (trimmed == null) {
      return null;
    }
    if (!StringUtils.isNumeric(trimmed)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unknown financial activity: " + trimmed);
    }
    return activityCatalog.resolveForTransaction(
        Long.parseLong(trimmed), requestedProperty == null ? null : requestedProperty.getId());
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

  private ConfirmedClassification classification(Income income) {
    return ConfirmedClassification.builder()
        .kind(ConfirmedClassification.Kind.INCOME)
        .sourceId(income.getSourceId())
        .activity(income.getActivity())
        .financialCategoryId(
            income.getFinancialCategory() == null ? null : income.getFinancialCategory().getId())
        .property(income.getProperty())
        .counterparty(income.getPayer())
        .build();
  }

  private VenmoStatementArchive archiveVenmoStatement(byte[] csvBytes, String originalFilename) {
    if (!receiptService.isConnected()) {
      return VenmoStatementArchive.empty();
    }
    String filename =
        StringUtils.defaultIfBlank(StringUtils.trimToNull(originalFilename), "venmo-statement.csv");
    UploadReceiptResponse uploaded = receiptService.uploadReceipt(filename, csvBytes);
    if (uploaded == null
        || uploaded.receipt() == null
        || StringUtils.isBlank(uploaded.receipt().id())) {
      log.warn(
          "Venmo statement upload returned no OneDrive ID; incomes will be saved without"
              + " attachment");
      return VenmoStatementArchive.empty();
    }
    return new VenmoStatementArchive(uploaded.receipt().id(), uploaded.receipt().name());
  }

  private void moveArchivedStatementToTaxYear(
      VenmoStatementArchive archive, Set<Integer> importedYears) {
    if (StringUtils.isBlank(archive.oneDriveId()) || importedYears.isEmpty()) {
      return;
    }
    int targetYear = importedYears.iterator().next();
    receiptService.moveTaxesFolder(archive.oneDriveId(), targetYear);
  }

  private record VenmoStatementArchive(String oneDriveId, String fileName) {
    private static VenmoStatementArchive empty() {
      return new VenmoStatementArchive(null, null);
    }
  }
}
