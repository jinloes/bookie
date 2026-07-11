package com.bookie.service;

import com.bookie.controller.ApiResponses;
import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.Payer;
import com.bookie.model.PendingIncome;
import com.bookie.model.PendingIncomeStatus;
import com.bookie.model.Property;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.model.UploadReceiptResponse;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.PayerPropertyHistoryRepository;
import com.bookie.repository.PendingIncomeRepository;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class IncomeService {

  private static final Pattern VENMO_HEADER_ROW =
      Pattern.compile("(?mi)^\\s*,?ID\\s*,.*Amount\\s*\\(total\\).*$");
  private static final List<String> TRANSACTION_ID_HEADERS =
      List.of("id", "transaction id", "payment id", "tx id");
  private static final List<String> SENDER_HEADERS =
      List.of("from", "from user", "from username", "sender", "actor");
  private static final List<String> AMOUNT_HEADERS =
      List.of("amount (total)", "amount", "net amount", "gross amount");
  private static final List<String> DATE_HEADERS =
      List.of("datetime", "date", "completed date", "created at", "time");
  private static final List<String> NOTE_HEADERS = List.of("note", "description", "memo");
  private static final List<DateTimeFormatter> DATE_FORMATS =
      List.of(
          DateTimeFormatter.ISO_LOCAL_DATE,
          DateTimeFormatter.ISO_LOCAL_DATE_TIME,
          DateTimeFormatter.ISO_DATE_TIME,
          DateTimeFormatter.ofPattern("M/d/yyyy"),
          DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"),
          DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss"));

  private final IncomeRepository incomeRepository;
  private final PropertyService propertyService;
  private final PayerService payerService;
  private final ReceiptService receiptService;
  private final PayerPropertyHistoryRepository payerPropertyHistoryRepository;
  private final PendingIncomeRepository pendingIncomeRepository;
  private final PropertyHistoryService propertyHistoryService;

  public List<Income> findAll() {
    return incomeRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
  }

  public Income findById(Long id) {
    return incomeRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income not found: " + id));
  }

  /** Returns whether an income already exists for the given source type and external source ID. */
  public boolean existsBySourceId(ExpenseSource sourceType, String sourceId) {
    return incomeRepository.existsBySourceTypeAndSourceId(sourceType, sourceId);
  }

  @Transactional
  public Income create(CreateIncomeRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Payer payer = req.payerId() != null ? payerService.findById(req.payerId()) : null;
    Income income =
        Income.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .source(req.source())
            .property(property)
            .payer(payer)
            .sourceType(req.sourceType())
            .receiptOneDriveId(req.receiptOneDriveId())
            .receiptFileName(req.receiptFileName())
            .build();
    income = incomeRepository.save(income);
    propertyHistoryService.record(income);
    return income;
  }

  @Transactional
  public ApiResponses.VenmoIncomeImportResponse importVenmoCsv(byte[] csvBytes, String payer)
      throws IOException {
    return importVenmoCsv(csvBytes, "venmo-statement.csv", payer, null);
  }

  @Transactional
  public ApiResponses.VenmoIncomeImportResponse importVenmoCsv(
      byte[] csvBytes, String originalFilename, String payer, String propertyIdStr)
      throws IOException {
    Payer selectedPayer = resolveSelectedPayer(payer);
    String senderFilter = selectedPayer != null ? selectedPayer.getName() : null;
    Property selectedProperty = resolveSelectedProperty(propertyIdStr);
    if (selectedProperty == null && selectedPayer != null) {
      selectedProperty = autoDetectPropertyForPayer(selectedPayer);
    }
    VenmoStatementArchive archive = archiveVenmoStatement(csvBytes, originalFilename);
    int totalRows = 0;
    int importedRows = 0;
    int skippedSenderRows = 0;
    int skippedOutgoingRows = 0;
    int skippedDuplicateRows = 0;
    int skippedInvalidRows = 0;
    Set<Integer> importedYears = new TreeSet<>();

    String csvContent = new String(csvBytes, StandardCharsets.UTF_8);
    String dataSection = extractVenmoDataSection(csvContent);

    try (Reader reader = new StringReader(dataSection);
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .build()
                .parse(reader)) {
      for (CSVRecord record : parser) {
        totalRows++;
        try {
          LinkedHashMap<String, String> row = normalizedRow(record.toMap());
          String sourceId = valueFor(row, TRANSACTION_ID_HEADERS);
          String sender = valueFor(row, SENDER_HEADERS);
          String note = valueFor(row, NOTE_HEADERS);
          BigDecimal amount = parseAmount(valueFor(row, AMOUNT_HEADERS));
          LocalDate date = parseDate(valueFor(row, DATE_HEADERS));
          String description = buildVenmoDescription(note, sender);

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
          Payer rowPayer = selectedPayer != null ? selectedPayer : resolvePayerBySender(sender);
          Property rowProperty =
              selectedProperty != null
                  ? selectedProperty
                  : (rowPayer != null ? autoDetectPropertyForPayer(rowPayer) : null);

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
                  .receiptOneDriveId(archive.oneDriveId())
                  .receiptFileName(archive.fileName())
                  .createdAt(LocalDateTime.now())
                  .build());
          importedRows++;
          importedYears.add(date.getYear());
        } catch (RuntimeException ex) {
          skippedInvalidRows++;
        }
      }
    }

    moveArchivedStatementToTaxYear(archive, importedYears);

    String propertyName = selectedProperty != null ? selectedProperty.getName() : null;
    return new ApiResponses.VenmoIncomeImportResponse(
        totalRows,
        importedRows,
        skippedSenderRows,
        skippedOutgoingRows,
        skippedDuplicateRows,
        skippedInvalidRows,
        senderFilter,
        propertyName);
  }

  @Transactional
  public Income save(Income income) {
    return incomeRepository.save(income);
  }

  @Transactional
  public Income update(Long id, UpdateIncomeRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Payer payer = req.payerId() != null ? payerService.findById(req.payerId()) : null;
    Income existing = findById(id);
    existing.setAmount(req.amount());
    existing.setDescription(req.description());
    existing.setDate(req.date());
    existing.setSource(req.source());
    existing.setProperty(property);
    existing.setPayer(payer);
    Income saved = incomeRepository.save(existing);
    propertyHistoryService.record(saved);
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    incomeRepository.deleteById(id);
  }

  @Transactional
  public void updateSourceId(Long id, String newSourceId) {
    incomeRepository
        .findById(id)
        .ifPresent(
            income -> {
              income.setSourceId(newSourceId);
              incomeRepository.save(income);
            });
  }

  public BigDecimal getTotalIncome() {
    return incomeRepository.getTotalIncome();
  }

  public List<PendingIncome> findAllPending() {
    return pendingIncomeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
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
    Property property =
        updates.propertyId() != null
            ? propertyService.findById(updates.propertyId())
            : pending.getProperty();
    Payer payer =
        updates.payerId() != null ? payerService.findById(updates.payerId()) : pending.getPayer();

    Income income =
        Income.builder()
            .amount(updates.amount() != null ? updates.amount() : pending.getAmount())
            .description(
                updates.description() != null ? updates.description() : pending.getDescription())
            .date(updates.date() != null ? updates.date() : pending.getDate())
            .source(updates.source() != null ? updates.source() : pending.getSource())
            .sourceId(pending.getSourceId())
            .sourceType(pending.getSourceType())
            .property(property)
            .payer(payer)
            .receiptOneDriveId(pending.getReceiptOneDriveId())
            .receiptFileName(pending.getReceiptFileName())
            .build();
    income = incomeRepository.save(income);
    propertyHistoryService.record(income);

    if (income.getReceiptOneDriveId() != null && income.getDate() != null) {
      receiptService.moveTaxesFolder(income.getReceiptOneDriveId(), income.getDate().getYear());
    }

    pendingIncomeRepository.deleteById(id);
    return income;
  }

  @Transactional
  public void rejectPendingIncome(Long id) {
    PendingIncome pending = findPendingById(id);
    pendingIncomeRepository.delete(pending);
  }

  private LinkedHashMap<String, String> normalizedRow(java.util.Map<String, String> rawRow) {
    LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
    for (var entry : rawRow.entrySet()) {
      String key = normalizeHeader(entry.getKey());
      if (StringUtils.isBlank(key) || normalized.containsKey(key)) {
        continue;
      }
      normalized.put(key, StringUtils.trimToNull(stripBom(entry.getValue())));
    }
    return normalized;
  }

  private String valueFor(LinkedHashMap<String, String> row, List<String> headerCandidates) {
    for (String candidate : headerCandidates) {
      String value = row.get(normalizeHeader(candidate));
      if (StringUtils.isNotBlank(value)) {
        return value;
      }
    }
    return null;
  }

  private String normalizeHeader(String header) {
    return stripBom(StringUtils.defaultString(header)).toLowerCase().replaceAll("[^a-z0-9]", "");
  }

  private String stripBom(String value) {
    if (value == null) {
      return null;
    }
    if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
      return value.substring(1);
    }
    return value;
  }

  private BigDecimal parseAmount(String value) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalArgumentException("Missing amount");
    }
    String cleaned = value.replace("$", "").replace(",", "").replaceAll("\\s+", "").trim();
    if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
      cleaned = "-" + cleaned.substring(1, cleaned.length() - 1);
    }
    return new BigDecimal(cleaned);
  }

  private LocalDate parseDate(String value) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalArgumentException("Missing date");
    }
    String trimmed = value.trim();
    for (DateTimeFormatter formatter : DATE_FORMATS) {
      try {
        if (formatter == DateTimeFormatter.ISO_DATE_TIME) {
          try {
            return OffsetDateTime.parse(trimmed, formatter).toLocalDate();
          } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(trimmed, formatter).toLocalDate();
          }
        }
        if (trimmed.contains(":")) {
          return LocalDateTime.parse(trimmed, formatter).toLocalDate();
        }
        return LocalDate.parse(trimmed, formatter);
      } catch (DateTimeParseException ignored) {
        // Keep trying known Venmo date formats.
      }
    }
    throw new IllegalArgumentException("Unsupported date format: " + value);
  }

  private boolean matchesSelectedPayer(String sender, Payer selectedPayer) {
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

  private String buildVenmoDescription(String note, String sender) {
    String cleanedNote = StringUtils.trimToNull(note);
    if (cleanedNote != null) {
      return "Venmo - " + cleanedNote;
    }
    String cleanedSender = StringUtils.trimToNull(sender);
    if (cleanedSender != null) {
      return "Venmo payment from " + StringUtils.removeStart(cleanedSender, "@");
    }
    return "Venmo payment";
  }

  private Payer resolveSelectedPayer(String payer) {
    String trimmed = StringUtils.trimToNull(payer);
    if (trimmed == null) {
      return null;
    }
    if (StringUtils.isNumeric(trimmed)) {
      return payerService.findById(Long.parseLong(trimmed));
    }
    return payerService
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
      return propertyService.findById(Long.parseLong(trimmed));
    }
    return null;
  }

  private Payer resolvePayerBySender(String sender) {
    String trimmed = StringUtils.trimToNull(sender);
    if (trimmed == null) {
      return null;
    }
    String normalized = StringUtils.removeStart(trimmed, "@");
    return payerService
        .findByName(normalized)
        .or(() -> payerService.findByAlias(normalized))
        .orElse(null);
  }

  private Property autoDetectPropertyForPayer(Payer payer) {
    return payerPropertyHistoryRepository
        .findByPayerIdOrderByOccurrencesDesc(payer.getId())
        .stream()
        .map(h -> h.getProperty())
        .filter(p -> p != null)
        .findFirst()
        .orElse(null);
  }

  private String extractVenmoDataSection(String csvContent) {
    Matcher headerMatcher = VENMO_HEADER_ROW.matcher(csvContent);
    if (headerMatcher.find()) {
      return csvContent.substring(headerMatcher.start());
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Could not find Venmo transaction header row in CSV.");
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
          "Venmo statement upload returned no OneDrive ID; incomes will be saved without attachment");
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
