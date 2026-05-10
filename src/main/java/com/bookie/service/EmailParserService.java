package com.bookie.service;

import com.bookie.model.EmailParseResult;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.Property;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Parses rental-related emails using an AI chat model for raw field extraction, then resolves
 * payers, properties, and categories via a deterministic Java lookup chain. This makes the result
 * correct even when the model does not call tools. Uses Spring Retry to handle transient model
 * failures (up to 3 attempts with exponential backoff).
 */
@Slf4j
@Service
public class EmailParserService {

  private static final int MAX_BODY_CHARS = 6_000;

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

  private static final String CATEGORY_LIST =
      String.join(", ", Arrays.stream(ExpenseCategory.values()).map(Enum::name).toList());

  private static final String SYSTEM_PROMPT =
      """
          You are a rental accounting assistant. Today is %1$s.

          Classify the email as EXPENSE or INCOME based solely on the direction of money:
          - INCOME: money received FROM a tenant — rent payments, rent receipts, security \
          deposits, or any notification that a tenant paid you. A subject containing "rent \
          receipt", "rent payment", or a property address/unit followed by "rent" is always \
          INCOME. Includes payment platform confirmations (Zelle, PayPal, Venmo, Buildium, \
          Cozy, AppFolio, RealPage) showing a tenant paid you. The word "receipt" alone does \
          NOT make an email an expense.
          - EXPENSE: money you (the landlord) paid OUT — a bill, invoice, or confirmation that \
          YOU paid a vendor: utilities, repairs, insurance, mortgage, HOA, trash, or other \
          rental operating cost. "Thank you for your payment" from a utility or service \
          provider (not a tenant) = EXPENSE. A "rent receipt" is never an expense.

          Extract the following fields:
          - emailType: EXPENSE or INCOME
          - amount: the grand total actually charged for EXPENSE (including tax and fees); \
          the amount received for INCOME. If the grand total was reduced to $0 by rewards \
          points or gift cards, use the item subtotal instead. Use 0 only if no dollar \
          amount can be found.
          - date: bill/invoice date if present, otherwise the Received date; ISO 8601 (YYYY-MM-DD)
          - description: "[Vendor/Tenant] - [Service or Items] [Month Year]"; \
          for service bills use the service type \
          (e.g. "PG&E - Electric Bill Feb 2026", "State Farm - Insurance Apr 2026"); \
          for retail receipts list simplified generic item names without brand or model details, \
          comma-separated (e.g. "Amazon - Painters Tape, Envelopes Apr 2026", \
          "Home Depot - Light Bulbs, Door Knob Mar 2026"); \
          for income use "Jane Smith - Rent Payment Mar 2026"
          - keywords: stable non-account identifiers from the email body \
          (invoice numbers, order numbers, confirmation codes, service addresses); \
          e.g. order number "113-4567890" is a keyword, account number "4-52819" is an accountNumber
          - accountNumbers: utility, customer, or service account numbers only — do NOT include \
          payment card last-four-digits (e.g. "Visa ending in 2108" → ignore "2108")
          - payerName: for INCOME, the tenant name; for EXPENSE, the vendor name exactly as it \
          appears in the email (e.g. "Amazon.com", "PG&E", "Bridgepointe HOA")
          - category: EXPENSE only — best-guess IRS Schedule E category using an exact enum key: \
          %2$s. Leave empty string ("") for INCOME. \
          Key distinctions: REPAIRS = labor or parts to fix something broken (plumber, \
          electrician, replacement fixture, broken appliance part); \
          SUPPLIES = consumable items not tied to a specific repair (tape, envelopes, \
          cleaning products, light bulbs, batteries, office supplies); \
          CLEANING_AND_MAINTENANCE = routine cleaning or preventive maintenance services. \
          Categorize based on the specific items purchased, not the vendor.

          Output ONLY the JSON object — no markdown fences, no preamble, no explanation. \
          The first character must be { and the last must be }:
          {"emailType":"","amount":0,"date":"","description":"","keywords":[],"accountNumbers":[],"payerName":"","category":""}
          """;

  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;
  private final PropertyRepository propertyRepository;
  private final PayerRepository payerRepository;
  private final EmailParserTools tools;

  public EmailParserService(
      @Qualifier("emailParserChatClient") ChatClient chatClient,
      ObjectMapper objectMapper,
      PropertyRepository propertyRepository,
      PayerRepository payerRepository,
      EmailParserTools tools) {
    this.chatClient = chatClient;
    this.objectMapper = objectMapper;
    this.propertyRepository = propertyRepository;
    this.payerRepository = payerRepository;
    this.tools = tools;
  }

  /**
   * Parses an email and returns a suggested expense or income pre-filled with extracted fields. The
   * AI model extracts raw text fields; payer, property, and category are resolved by a
   * deterministic Java lookup chain so the result is correct even when the model skips lookups.
   *
   * @param subject the email subject line
   * @param body the email body text
   * @param receivedDate the date the email was received
   * @return a suggestion with extracted fields; {@code sourceType} and {@code sourceId} are left
   *     null for the caller to populate
   * @throws RuntimeException if parsing fails after all retry attempts
   */
  @CircuitBreaker(name = "ollamaClient", fallbackMethod = "ollamaCircuitBreakerFallback")
  @Retryable(backoff = @Backoff(delay = 500, multiplier = 2))
  public EmailSuggestion suggestFromEmail(String subject, String body, String receivedDate) {
    long start = System.currentTimeMillis();
    String json =
        chatClient
            .prompt()
            .system(SYSTEM_PROMPT.formatted(LocalDate.now(), CATEGORY_LIST))
            .messages(
                List.of(
                    new UserMessage(buildUserMessage(subject, body, receivedDate)),
                    new AssistantMessage("<think>\n\n</think>")))
            .call()
            .content();
    log.info(
        "LLM [email-parser]: {}ms — subject: '{}'", System.currentTimeMillis() - start, subject);
    if (StringUtils.isBlank(json)) {
      throw new IllegalStateException("Email parser returned empty response");
    }
    EmailParseResult result;
    try {
      result = objectMapper.readValue(json, EmailParseResult.class);
    } catch (Exception e) {
      throw new IllegalStateException("Email parser returned invalid JSON: " + json, e);
    }
    log.debug("Parse result: {}", result);
    List<Property> knownProperties = propertyRepository.findAll();
    // Skip payer resolution for INCOME — the tenant name needs no DB lookup.
    boolean isIncome = result.emailType() == EmailType.INCOME;
    String resolvedPayerName =
        isIncome ? StringUtils.trimToNull(result.payerName()) : resolvePayer(result);
    log.debug("suggestFromEmail: resolvedPayerName='{}' isIncome={}", resolvedPayerName, isIncome);
    return EmailSuggestion.builder()
        .emailType(result.emailType() != null ? result.emailType() : EmailType.EXPENSE)
        .amount(result.amount())
        .description(result.description())
        .date(normalizeDate(result.date()))
        .category(isIncome ? null : resolveCategory(result, resolvedPayerName))
        .propertyName(resolveProperty(result, body, knownProperties))
        .payerName(resolvedPayerName)
        .keywords(result.keywords())
        .accountNumbers(result.accountNumbers())
        .build();
  }

  /**
   * Resolves the property name via a four-step lookup chain:
   *
   * <ol>
   *   <li>Account numbers → DB lookup
   *   <li>Payer/keyword history hints
   *   <li>Property street address matching against the email body
   *   <li>Single-property fallback
   * </ol>
   */
  private String resolveProperty(
      EmailParseResult result, String emailBody, List<Property> knownProperties) {
    if (!CollectionUtils.isEmpty(result.accountNumbers())) {
      List<String> found = tools.findPropertyByAccount(result.accountNumbers());
      if (!found.isEmpty()) {
        return found.get(0);
      }
    }
    // null keywords are handled safely by getPropertyHints
    List<String> hints = tools.getPropertyHints(result.payerName(), result.keywords());
    if (!hints.isEmpty()) {
      return extractFromHint(hints.get(0));
    }
    // Match stored property addresses against the email body (e.g. Amazon shipping address).
    // Uses "streetNumber streetName" (e.g. "41784 Wild") to avoid false mismatches from
    // period/comma placement or city suffixes in the stored address string.
    log.debug("resolveProperty: address scan over {} known properties", knownProperties.size());
    if (StringUtils.isNotBlank(emailBody)) {
      for (Property p : knownProperties) {
        String streetKey = streetKey(p.getAddress());
        if (streetKey != null) {
          boolean matched = StringUtils.containsIgnoreCase(emailBody, streetKey);
          log.debug("  '{}' streetKey='{}' matched={}", p.getName(), streetKey, matched);
          if (matched) {
            return p.getName();
          }
        } else {
          log.debug("  '{}' has no address configured", p.getName());
        }
      }
    }
    if (knownProperties.size() == 1) {
      log.debug(
          "resolveProperty: single-property fallback -> '{}'", knownProperties.get(0).getName());
      return knownProperties.get(0).getName();
    }
    return null;
  }

  /**
   * Resolves the canonical payer name via a four-step lookup chain:
   *
   * <ol>
   *   <li>Account numbers → DB lookup
   *   <li>Exact name match in DB (case-insensitive)
   *   <li>Alias lookup — also records unrecognized short names for later auto-save
   *   <li>Keyword history hints
   * </ol>
   *
   * Falls back to the raw vendor name from the email if all lookups fail.
   */
  private String resolvePayer(EmailParseResult result) {
    if (!CollectionUtils.isEmpty(result.accountNumbers())) {
      List<String> found = tools.findPayerByAccountNumber(result.accountNumbers());
      if (!found.isEmpty()) {
        return found.get(0);
      }
    }
    String rawName = StringUtils.trimToNull(result.payerName());
    if (rawName == null) {
      return null;
    }
    Optional<String> exactMatch =
        payerRepository.findByNameIgnoreCase(rawName).map(p -> p.getName());
    if (exactMatch.isPresent()) {
      return exactMatch.get();
    }
    List<String> aliasResult = tools.findPayerByAlias(List.of(rawName));
    if (!aliasResult.isEmpty()) {
      return aliasResult.get(0);
    }
    if (!CollectionUtils.isEmpty(result.keywords())) {
      List<String> hints = tools.getPayerHints(result.keywords());
      if (!hints.isEmpty()) {
        return extractFromHint(hints.get(0));
      }
    }
    return rawName;
  }

  /**
   * Resolves the expense category via a three-step priority chain:
   *
   * <ol>
   *   <li>Keyword history hints (most specific)
   *   <li>Payer history hints
   *   <li>AI model's best-guess (fallback)
   * </ol>
   */
  private String resolveCategory(EmailParseResult result, String resolvedPayerName) {
    if (!CollectionUtils.isEmpty(result.keywords())) {
      List<String> hints = tools.getCategoryHints(result.keywords());
      if (!hints.isEmpty()) {
        String category = sanitizeCategory(extractFromHint(hints.get(0)));
        if (category != null) {
          return category;
        }
      }
    }
    String payerToCheck =
        StringUtils.isNotBlank(resolvedPayerName)
            ? resolvedPayerName
            : StringUtils.trimToNull(result.payerName());
    if (StringUtils.isNotBlank(payerToCheck)) {
      List<String> hints = tools.getCategoryForPayer(List.of(payerToCheck));
      if (!hints.isEmpty()) {
        String category = sanitizeCategory(extractFromHint(hints.get(0)));
        if (category != null) {
          return category;
        }
      }
    }
    return sanitizeCategory(result.category());
  }

  /**
   * Extracts the resolved value from a history hint string. Handles both "Label → VALUE (N times)"
   * and "VALUE (N times)" formats.
   */
  private String extractFromHint(String hint) {
    if (hint == null) {
      return null;
    }
    int arrow = hint.indexOf(" → ");
    int paren = hint.lastIndexOf(" (");
    if (arrow >= 0 && paren > arrow) {
      return hint.substring(arrow + 3, paren).trim();
    }
    if (paren > 0) {
      return hint.substring(0, paren).trim();
    }
    return hint.trim();
  }

  /**
   * Extracts "streetNumber streetName" from an address (e.g. "41784 Wild" from "41784 Wild Indigo
   * Ter. Fremont, CA"). Using just the first two tokens avoids false mismatches caused by period
   * placement, missing commas, or abbreviation differences in the stored address.
   */
  private String streetKey(String address) {
    if (StringUtils.isBlank(address)) {
      return null;
    }
    String[] words = address.trim().split("\\s+");
    if (words.length >= 2 && words[0].matches("\\d+")) {
      return words[0] + " " + words[1];
    }
    return null;
  }

  private String buildUserMessage(String subject, String body, String receivedDate) {
    String date = StringUtils.isNotBlank(receivedDate) ? receivedDate : LocalDate.now().toString();
    String safeBody =
        body != null && body.length() > MAX_BODY_CHARS
            ? body.substring(0, MAX_BODY_CHARS) + "…[truncated]"
            : body;
    return """
        Received: %s
        Subject: %s

        <email_body>
        %s
        </email_body>
        /no_think
        """
        .formatted(date, subject, safeBody);
  }

  private String normalizeDate(String raw) {
    if (StringUtils.isBlank(raw)) {
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

  private String sanitizeCategory(String category) {
    if (StringUtils.isBlank(category)) {
      return null;
    }
    try {
      ExpenseCategory.valueOf(category.trim().toUpperCase());
      return category.trim().toUpperCase();
    } catch (IllegalArgumentException e) {
      log.warn("Unrecognized category '{}', discarding", category);
      return null;
    }
  }

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

  // Called by Resilience4j when the circuit breaker is open (Ollama is down)
  public EmailSuggestion ollamaCircuitBreakerFallback(
      String subject, String body, String receivedDate, CallNotPermittedException e) {
    log.error("Email parser circuit breaker is OPEN for subject '{}': {}", subject, e.getMessage());
    throw new RuntimeException("Email parser service is temporarily unavailable", e);
  }
}
