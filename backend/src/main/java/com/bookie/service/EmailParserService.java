package com.bookie.service;

import com.bookie.model.EmailParseResult;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.FinancialActivity;
import com.bookie.model.HistoryHint;
import com.bookie.model.Property;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import com.bookie.util.DateParserUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Parses cashflow-related emails using an AI chat model for raw field extraction, then resolves the
 * activity, category, counterparty, and any rental property through deterministic repository and
 * history lookups. The model never selects an owner or tax treatment.
 */
@Slf4j
@Service
public class EmailParserService {

  private static final int MAX_BODY_CHARS = 6_000;

  private static final String SYSTEM_PROMPT =
      """
          Extract a proposed household cashflow record from an email. Today is %1$s.

          Classify direction solely from the household's cash movement:
          - INCOME: money received, including rent, a paycheck deposit, tutoring payment, or \
          reimbursement. For a paycheck notice, extract only the deposited/net amount shown; \
          never infer gross wages or withholding.
          - EXPENSE: money paid out, including a bill, invoice, purchase, or payment confirmation.
          A receipt documenting money received is INCOME; the word "receipt" alone does not \
          determine direction.

          Extract the following fields:
          - direction: EXPENSE or INCOME
          - amount: the grand total actually charged for EXPENSE (including tax and fees); \
          the amount actually received for INCOME. Use 0 only if no dollar amount can be found.
          - date: bill/invoice date if present, otherwise the Received date; ISO 8601 (YYYY-MM-DD)
          - description: a concise, factual description of the payment, deposit, service, or items
          - keywords: stable non-account identifiers from the email body \
          (invoice numbers, order numbers, confirmation codes, or service references)
          - accountNumbers: utility, customer, or service account numbers only — do NOT include \
          payment-card last-four-digits
          - counterpartyName: the employer, customer, tenant, vendor, or reimbursing organization \
          exactly as it appears

          Do not output an activity, owner, property, category, or tax treatment. Those fields are \
          resolved deterministically from configured import context and confirmed history.

          Output ONLY the JSON object — no markdown fences, no preamble, no explanation. \
          The first character must be { and the last must be }:
          {"direction":"","amount":0,"date":"","description":"","keywords":[],"accountNumbers":[],"counterpartyName":""}
          """;

  private final LlmGateway llmGateway;
  private final ObjectMapper objectMapper;
  private final PropertyRepository propertyRepository;
  private final PayerRepository payerRepository;
  private final EmailParserTools tools;
  private final EmailParserToolDefinitions toolDefinitions;
  private final SuggestionValidator suggestionValidator;
  private final AutomatedIntakeClassificationService classificationService;

  @Value("${ai.model.chat}")
  private String chatModel;

  @Value("${ai.tools.email-parser.enabled:false}")
  private boolean emailParserToolsEnabled;

  public EmailParserService(
      LlmGateway llmGateway,
      ObjectMapper objectMapper,
      PropertyRepository propertyRepository,
      PayerRepository payerRepository,
      EmailParserTools tools,
      EmailParserToolDefinitions toolDefinitions,
      SuggestionValidator suggestionValidator,
      AutomatedIntakeClassificationService classificationService) {
    this.llmGateway = llmGateway;
    this.objectMapper = objectMapper;
    this.propertyRepository = propertyRepository;
    this.payerRepository = payerRepository;
    this.tools = tools;
    this.toolDefinitions = toolDefinitions;
    this.suggestionValidator = suggestionValidator;
    this.classificationService = classificationService;
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
  public EmailSuggestion suggestFromEmail(String subject, String body, String receivedDate) {
    return suggestFromEmail(subject, body, receivedDate, null);
  }

  @CircuitBreaker(name = "aiClient", fallbackMethod = "aiClientCircuitBreakerFallback")
  @Retryable(backoff = @Backoff(delay = 500, multiplier = 2))
  public EmailSuggestion suggestFromEmail(
      String subject, String body, String receivedDate, Long configuredActivityId) {
    long start = System.currentTimeMillis();
    String json =
        llmGateway.completeText(
            LlmTextRequest.builder()
                .model(chatModel)
                .systemPrompt(SYSTEM_PROMPT.formatted(LocalDate.now()))
                .userPrompt(buildUserMessage(subject, body, receivedDate))
                .tools(emailParserToolsEnabled ? toolDefinitions.createTools() : List.of())
                .build());
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
    TransactionDirection direction =
        result.direction() != null ? result.direction() : TransactionDirection.EXPENSE;
    boolean isIncome = direction == TransactionDirection.INCOME;
    String resolvedPayerName =
        isIncome ? StringUtils.trimToNull(result.counterpartyName()) : resolvePayer(result);
    log.debug("suggestFromEmail: resolvedPayerName='{}' isIncome={}", resolvedPayerName, isIncome);
    String resolvedPropertyName =
        configuredActivityId == null ? resolveProperty(result, body, knownProperties) : null;
    AutomatedIntakeClassificationService.Resolution classification =
        classificationService.resolve(
            direction,
            configuredActivityId,
            resolvedPropertyName,
            result.keywords(),
            resolvedPayerName);
    FinancialActivity activity = classification.activity();
    String activityPropertyName =
        activity.getProperty() != null ? activity.getProperty().getName() : resolvedPropertyName;
    EmailSuggestion suggestion =
        EmailSuggestion.builder()
            .emailType(EmailType.valueOf(direction.name()))
            .amount(result.amount())
            .description(result.description())
            .date(normalizeDate(result.date(), receivedDate))
            .category(classification.category().getKey())
            .propertyName(activityPropertyName)
            .payerName(resolvedPayerName)
            .keywords(result.keywords())
            .accountNumbers(result.accountNumbers())
            .activityId(activity.getId())
            .categoryId(classification.category().getId())
            .classificationAmbiguous(classification.classificationAmbiguous())
            .build();
    return suggestionValidator.validate(suggestion, result.counterpartyName(), knownProperties);
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
    List<HistoryHint> hints = tools.getPropertyHints(result.counterpartyName(), result.keywords());
    if (!hints.isEmpty()) {
      return hints.get(0).value();
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
    String rawName = StringUtils.trimToNull(result.counterpartyName());
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
      List<HistoryHint> hints = tools.getPayerHints(result.keywords());
      if (!hints.isEmpty()) {
        return hints.get(0).value();
      }
    }
    return rawName;
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
    String date = StringUtils.defaultIfBlank(receivedDate, "unknown");
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
        """
        .formatted(date, subject, safeBody);
  }

  private String normalizeDate(String raw, String receivedDate) {
    String parsedFromRaw = parseDate(raw);
    if (parsedFromRaw != null) {
      return parsedFromRaw;
    }

    String parsedFromReceived = parseDate(receivedDate);
    if (parsedFromReceived != null) {
      if (StringUtils.isNotBlank(raw)) {
        log.warn("Unrecognized extracted date '{}', falling back to received date", raw);
      }
      return parsedFromReceived;
    }

    if (StringUtils.isNotBlank(raw)) {
      log.warn("Unrecognized date format '{}' and no valid received date; leaving date empty", raw);
    }
    return null;
  }

  private String parseDate(String value) {
    return DateParserUtil.parseDateAsIsoString(value);
  }

  // Spring Retry requires the recover method signature to mirror the retried method's parameters
  @Recover
  public EmailSuggestion recoverSuggestFromEmail(
      Exception e, String subject, String body, String receivedDate, Long configuredActivityId) {
    log.warn(
        "Email parsing failed for subject '{}' after all retries; returning partial parse: {}",
        subject,
        e.getMessage());

    return partialSuggestion(subject, receivedDate, configuredActivityId);
  }

  // Called by Resilience4j when the circuit breaker is open (AI service is unavailable)
  public EmailSuggestion aiClientCircuitBreakerFallback(
      String subject,
      String body,
      String receivedDate,
      Long configuredActivityId,
      CallNotPermittedException e) {
    log.error(
        "Email parser circuit breaker is OPEN for subject '{}'; returning partial parse. {}",
        subject,
        e.getMessage());

    return partialSuggestion(subject, receivedDate, configuredActivityId);
  }

  private EmailSuggestion partialSuggestion(
      String subject, String receivedDate, Long configuredActivityId) {
    AutomatedIntakeClassificationService.Resolution classification =
        classificationService.resolve(
            TransactionDirection.EXPENSE, configuredActivityId, null, List.of(), null);
    FinancialActivity activity = classification.activity();
    String parsedDate = parseDate(receivedDate);
    return EmailSuggestion.builder()
        .emailType(null)
        .amount(null)
        .description(extractDescriptionFromSubject(subject))
        .date(parsedDate != null ? parsedDate : receivedDate)
        .category(classification.category().getKey())
        .propertyName(activity.getProperty() != null ? activity.getProperty().getName() : null)
        .payerName(null)
        .keywords(List.of())
        .accountNumbers(List.of())
        .activityId(activity.getId())
        .categoryId(classification.category().getId())
        .classificationAmbiguous(true)
        .build();
  }

  private String extractDescriptionFromSubject(String subject) {
    if (StringUtils.isBlank(subject)) {
      return "[Email parsing failed; please fill in details]";
    }
    return subject.length() > 200 ? subject.substring(0, 200) + "..." : subject;
  }
}
