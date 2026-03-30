package com.bookie.service;

import com.bookie.model.EmailParseResult;
import com.bookie.model.ExpenseSuggestion;
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
 * Parses rental-related emails using an AI chat model and extracts expense details into an {@link
 * ExpenseSuggestion}. Uses Spring Retry to handle transient model failures (up to 3 attempts with
 * exponential backoff).
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
          Extract rental expense details from the email. Today is %s.

          Date: use the bill or invoice date from the email body if present; \
          otherwise fall back to the Received date in the message header. \
          Always return in ISO 8601 format (YYYY-MM-DD).

          Keywords: extract stable identifiers (account numbers, invoice numbers, \
          reference codes, service addresses) from the body, then call getPayerHints \
          and getPropertyHints with them to identify the payer and property.

          Prefer exact known names from the tools over raw values from the email.
          """;

  private final ChatClient chatClient;
  private final EmailParserTools tools;

  public EmailParserService(ChatClient.Builder builder, EmailParserTools tools) {
    this.chatClient = builder.build();
    this.tools = tools;
  }

  /**
   * Parses an email and returns a suggested expense pre-filled with extracted fields. The AI model
   * uses {@link EmailParserTools} to resolve categories, payers, and properties to known values,
   * and history hints to improve accuracy based on past confirmed expenses.
   *
   * @param subject the email subject line
   * @param body the email body text
   * @return a suggested expense with extracted fields; {@code sourceType} and {@code sourceId} are
   *     left null for the caller to populate
   * @throws RuntimeException if parsing fails after all retry attempts
   */
  @Retryable(backoff = @Backoff(delay = 500, multiplier = 2))
  public ExpenseSuggestion suggestExpenseFromEmail(
      String subject, String body, String receivedDate) {
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
    return ExpenseSuggestion.builder()
        .amount(result.amount())
        .description(result.description())
        .date(normalizeDate(result.date()))
        .category(result.category())
        .propertyName(result.propertyName())
        .payerName(result.payerName())
        .keywords(result.keywords())
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
  public ExpenseSuggestion recoverSuggestExpenseFromEmail(
      Exception e, String subject, String body, String receivedDate) {
    log.error(
        "Email parsing failed for subject '{}' after all retries: {}", subject, e.getMessage());
    throw new RuntimeException("Email parsing failed after retries", e);
  }
}
