package com.bookie.integrations.venmo;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class VenmoCsvInputAdapter implements VenmoInputPort {

  private static final Pattern HEADER_ROW =
      Pattern.compile("(?mi)^\\s*,?ID\\s*,.*Amount\\s*\\(total\\).*$");
  private static final Pattern HEADER_NORMALIZATION = Pattern.compile("[^a-z0-9]");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
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

  @Override
  public VenmoStatement parse(byte[] csvBytes) throws IOException {
    if (csvBytes == null) {
      throw new VenmoInputException("Venmo statement is required.");
    }
    String dataSection = extractDataSection(new String(csvBytes, StandardCharsets.UTF_8));
    int totalRows = 0;
    int invalidRows = 0;
    List<VenmoTransaction> transactions = new ArrayList<>();

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
          String sender = valueFor(row, SENDER_HEADERS);
          transactions.add(
              VenmoTransaction.builder()
                  .sourceId(valueFor(row, TRANSACTION_ID_HEADERS))
                  .sender(sender)
                  .amount(parseAmount(valueFor(row, AMOUNT_HEADERS)))
                  .date(parseDate(valueFor(row, DATE_HEADERS)))
                  .description(buildDescription(valueFor(row, NOTE_HEADERS), sender))
                  .build());
        } catch (RuntimeException e) {
          invalidRows++;
        }
      }
    }
    return VenmoStatement.builder()
        .totalRows(totalRows)
        .invalidRows(invalidRows)
        .transactions(transactions)
        .build();
  }

  private static String extractDataSection(String csvContent) {
    Matcher headerMatcher = HEADER_ROW.matcher(csvContent);
    if (headerMatcher.find()) {
      return csvContent.substring(headerMatcher.start());
    }
    throw new VenmoInputException("Could not find Venmo transaction header row in CSV.");
  }

  private static LinkedHashMap<String, String> normalizedRow(Map<String, String> rawRow) {
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

  private static String valueFor(LinkedHashMap<String, String> row, List<String> headerCandidates) {
    for (String candidate : headerCandidates) {
      String value = row.get(normalizeHeader(candidate));
      if (StringUtils.isNotBlank(value)) {
        return value;
      }
    }
    return null;
  }

  private static String normalizeHeader(String header) {
    return HEADER_NORMALIZATION
        .matcher(stripBom(StringUtils.defaultString(header)).toLowerCase())
        .replaceAll("");
  }

  private static String stripBom(String value) {
    if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
      return value.substring(1);
    }
    return value;
  }

  private static BigDecimal parseAmount(String value) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalArgumentException("Missing amount");
    }
    String cleaned =
        WHITESPACE.matcher(value.replace("$", "").replace(",", "")).replaceAll("").trim();
    if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
      cleaned = "-" + cleaned.substring(1, cleaned.length() - 1);
    }
    return new BigDecimal(cleaned);
  }

  private static LocalDate parseDate(String value) {
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
        // Continue through the supported Venmo date formats.
      }
    }
    throw new IllegalArgumentException("Unsupported date format: " + value);
  }

  private static String buildDescription(String note, String sender) {
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
}
