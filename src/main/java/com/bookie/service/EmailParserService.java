package com.bookie.service;

import com.bookie.model.EmailParseResult;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
          Classify and extract rental financial details from the email. Today is %s.

          First, classify the email as EXPENSE or INCOME:
          - EXPENSE: a bill, invoice, or payment made for a service, repair, utility, insurance, \
          mortgage, or other rental operating cost.
          - INCOME: a rent payment, deposit, or other money received from a tenant.

          Set emailType to EXPENSE or INCOME accordingly.

          If EXPENSE, follow these steps in order:

          1. Extract stable identifiers from the email body. Split them into two lists:
             - keywords: invoice numbers, reference codes, service addresses, and any \
          other stable identifiers that are NOT account numbers.
             - accountNumbers: account numbers only (e.g. customer account number, \
          utility account number). These will be saved to the payer record.

          2. Call findPayerByAccountNumber with the extracted accountNumbers. If a match \
          is returned, use that payer name exactly. Otherwise call getPayerHints with all \
          keywords and accountNumbers combined; if results are returned use the top-ranked \
          name. If both return empty, \
          call getKnownPayers and find the closest match to the payer in the email. Always \
          use the exact name returned by the tool — never the abbreviated or alternate \
          name from the email (e.g. if the tool returns "Pacific Gas and Electric Company" \
          use that, not "PG&E").

          3. Call getCategoryForPayer with the identified payer name. If results are \
          returned, use the top-ranked category. Otherwise call getExpenseCategories \
          and pick the best match.

          4. Call getPropertyHints with the identified payer name and all extracted keywords. \
          If results are returned, use the top-ranked property name exactly as returned. \
          If getPropertyHints returns empty, you MUST call getKnownProperties, then compare \
          every keyword you extracted (account numbers, reference codes, service addresses) \
          against the name and address of each property in the result. Pick the closest \
          match. If no keyword matches, pick the most likely property by context. \
          Always use the exact property name returned by the tool and never leave \
          propertyName blank.

          5. Return the result. If no payer match is found, use the raw value from the email.

          If INCOME, skip the payer/category steps. Only extract: amount, date, description, \
          and propertyName. Call getPropertyHints with any keywords and the tenant name; \
          if empty, call getKnownProperties and match by address. Leave category and payerName null.

          Description: a concise human-readable label following this format:
             "[Payer/Source] - [Service Type / Payment Type] [Billing Period]"
          Rules:
          - Always include the payer name (EXPENSE) or tenant/source name (INCOME).
          - Include the billing period (month + year) if found in the email, e.g. "Mar 2025".
          - For utilities: include the utility type, e.g. "ACWD - Water Service Mar 2025", \
          "PG&E - Electric Bill Feb 2025".
          - For repairs/maintenance: include what was repaired, e.g. \
          "Bob's Plumbing - Pipe Repair", "ABC Landscaping - Monthly Maintenance Mar 2025".
          - For insurance: include the coverage type, e.g. \
          "State Farm - Homeowners Insurance Mar 2025".
          - For mortgage/interest: include the loan reference if available, e.g. \
          "Wells Fargo - Mortgage Payment Mar 2025".
          - For rent income: e.g. "Tenant Name - Rent Payment Mar 2025".
          - Use the email subject as a hint if the body lacks detail.

          Date: use the bill or invoice date from the email body if present; otherwise fall \
          back to the Received date. Always return in ISO 8601 format (YYYY-MM-DD).
          """;

  private final ChatClient chatClient;
  private final EmailParserTools tools;

  public EmailParserService(ChatClient.Builder builder, EmailParserTools tools) {
    this.chatClient = builder.build();
    this.tools = tools;
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
    EmailParseResult result =
        chatClient
            .prompt()
            .system(SYSTEM_PROMPT.formatted(LocalDate.now()))
            .user(buildUserMessage(subject, body, receivedDate))
            .tools(tools)
            .call()
            .entity(EmailParseResult.class);
    if (result == null) {
      throw new IllegalStateException("Email parser returned null result");
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
