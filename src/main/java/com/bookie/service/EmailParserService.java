package com.bookie.service;

import com.bookie.model.EmailParseResult;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Parses rental-related emails using an AI chat model and extracts expense or income details into
 * an {@link EmailSuggestion}. Uses tools to resolve payers, properties, and categories from the
 * database. Uses Spring Retry to handle transient model failures (up to 3 attempts with exponential
 * backoff).
 */
@Slf4j
@Service
public class EmailParserService {

  private static final List<DateTimeFormatter> DATE_FORMATS =
      List.of(
          DateTimeFormatter.ISO_LOCAL_DATE,
          DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
          DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
          DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.US),
          caseInsensitive("MMMM d, yyyy"),
          caseInsensitive("MMM d, yyyy"));

  private static DateTimeFormatter caseInsensitive(String pattern) {
    return new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern(pattern)
        .toFormatter(Locale.US);
  }

  private static final String SYSTEM_PROMPT =
      """
          You are a rental accounting assistant. Today is %s.

          Classify the email as EXPENSE or INCOME:
          - EXPENSE: a bill, invoice, payment confirmation, or receipt for a service you paid — \
          utilities, repairs, insurance, mortgage, trash, or other rental operating cost. \
          "Thank you for your payment" emails from a company = EXPENSE.
          - INCOME: rent, deposit, or other money received FROM a tenant paying you.

          Extract the following fields. Use the available tools to resolve payerName, \
          propertyName, and category — do not guess values that a tool can look up.

          Fields:
          - emailType: EXPENSE or INCOME
          - amount: the total amount due or received
          - date: bill/invoice date if present, otherwise the Received date; ISO 8601 (YYYY-MM-DD)
          - description: "[Payer/Tenant] - [Service/Payment Type] [Month Year]" \
          e.g. "PG&E - Electric Bill Feb 2026", "Jane Smith - Rent Payment Mar 2026"
          - keywords: stable non-account identifiers from the email body \
          (invoice numbers, reference codes, confirmation numbers, service addresses)
          - accountNumbers: account numbers only (customer account, utility account numbers)
          - payerName: the company or person this expense/income is from; \
          resolved via tools for EXPENSE; for INCOME use the tenant name from the email
          - category: resolved via tools (EXPENSE only); use exact enum key e.g. UTILITIES
          - propertyName: you MUST call the property tools to resolve this — never skip them. \
          If the email contains account numbers, call findPropertyByAccount first; \
          if it returns empty, call getPropertyHints next. \
          Leave propertyName blank only after all property tools have been called and returned empty.

          Respond with a single JSON object matching this schema, no markdown:
          {"emailType":"","amount":0,"date":"","description":"","keywords":[],"accountNumbers":[],"payerName":"","category":"","propertyName":""}
          """;

  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;

  public EmailParserService(
      @Qualifier("emailParserChatClient") ChatClient chatClient, ObjectMapper objectMapper) {
    this.chatClient = chatClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Parses an email and returns a suggested expense or income pre-filled with extracted fields.
   *
   * @param subject the email subject line
   * @param body the email body text
   * @param receivedDate the date the email was received
   * @return a suggestion with extracted fields; {@code sourceType} and {@code sourceId} are left
   *     null for the caller to populate
   * @throws RuntimeException if parsing fails after all retry attempts
   */
  @Retryable(backoff = @Backoff(delay = 500, multiplier = 2))
  public EmailSuggestion suggestFromEmail(String subject, String body, String receivedDate) {
    String json =
        chatClient
            .prompt()
            .system(SYSTEM_PROMPT.formatted(LocalDate.now()))
            .user(buildUserMessage(subject, body, receivedDate))
            .call()
            .content();
    if (!StringUtils.hasText(json)) {
      throw new IllegalStateException("Email parser returned empty response");
    }
    EmailParseResult result;
    try {
      result = objectMapper.readValue(json, EmailParseResult.class);
    } catch (Exception e) {
      throw new IllegalStateException("Email parser returned invalid JSON: " + json, e);
    }
    log.debug("Parse result: {}", result);
    return EmailSuggestion.builder()
        .emailType(result.emailType() != null ? result.emailType() : EmailType.EXPENSE)
        .amount(result.amount())
        .description(result.description())
        .date(normalizeDate(result.date()))
        .category(blankToNull(result.category()))
        .propertyName(blankToNull(result.propertyName()))
        .payerName(blankToNull(result.payerName()))
        .keywords(result.keywords())
        .accountNumbers(result.accountNumbers())
        .build();
  }

  private String buildUserMessage(String subject, String body, String receivedDate) {
    String date = StringUtils.hasText(receivedDate) ? receivedDate : LocalDate.now().toString();
    return "Received: %s\nSubject: %s\n\n%s".formatted(date, subject, body);
  }

  private String normalizeDate(String raw) {
    if (!StringUtils.hasText(raw)) {
      return LocalDate.now().toString();
    }
    return DATE_FORMATS.stream()
        .flatMap(fmt -> tryParse(raw.trim(), fmt).stream())
        .findFirst()
        .orElseGet(
            () -> {
              log.warn("Unrecognized date format '{}', falling back to today", raw);
              return LocalDate.now().toString();
            });
  }

  private String blankToNull(String value) {
    return StringUtils.hasText(value) ? value : null;
  }

  // Returns empty on a parse failure so the caller can try the next format
  private Optional<String> tryParse(String value, DateTimeFormatter fmt) {
    try {
      return Optional.of(LocalDate.parse(value, fmt).toString());
    } catch (DateTimeParseException ignored) {
      return Optional.empty();
    }
  }

  // Spring Retry requires the recover method signature to mirror the retried method's parameters
  @Recover
  public EmailSuggestion recoverSuggestFromEmail(
      Exception e, String subject, String body, String receivedDate) {
    log.error(
        "Email parsing failed for subject '{}' after all retries: {}", subject, e.getMessage());
    throw new RuntimeException("Email parsing failed after retries", e);
  }
}
