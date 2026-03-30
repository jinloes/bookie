package com.bookie.service;

import com.bookie.model.ExpenseSuggestion;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Parses rental-related emails using an AI chat model and extracts expense details into an {@link
 * ExpenseSuggestion}. Uses Spring Retry to handle transient model failures (up to 3 attempts with
 * exponential backoff).
 */
@Slf4j
@Service
public class EmailParserService {

  record EmailParseResult(
      Double amount,
      String description,
      String date,
      String category,
      String propertyName,
      String payerName) {}

  private static final String SYSTEM_PROMPT =
      """
          Extract rental expense details from the email. Today's date is %s.
          Use the available tools to resolve categories, payers, and properties.
          Prefer exact known names over raw values from the email.
          """;

  private final ChatClient chatClient;
  private final EmailParserTools tools;

  public EmailParserService(ChatClient.Builder builder, EmailParserTools tools) {
    this.chatClient = builder.build();
    this.tools = tools;
  }

  /**
   * Parses an email and returns a suggested expense pre-filled with extracted fields. The AI model
   * uses {@link EmailParserTools} to resolve categories, payers, and properties to known values.
   *
   * @param subject the email subject line
   * @param body the email body text
   * @return a suggested expense with extracted fields; {@code sourceType} and {@code sourceId} are
   *     left null for the caller to populate
   * @throws RuntimeException if parsing fails after all retry attempts
   */
  @Retryable(backoff = @Backoff(delay = 500, multiplier = 2))
  public ExpenseSuggestion suggestExpenseFromEmail(String subject, String body) {
    EmailParseResult result =
        chatClient
            .prompt()
            .system(SYSTEM_PROMPT.formatted(LocalDate.now()))
            .user("Subject: %s\n\n%s".formatted(subject, body))
            .tools(tools)
            .call()
            .entity(EmailParseResult.class);
    if (result == null) {
      throw new IllegalStateException("Email parser returned null result");
    }
    return new ExpenseSuggestion(
        result.amount(),
        result.description(),
        result.date(),
        result.category(),
        result.propertyName(),
        result.payerName(),
        null,
        null);
  }

  @Recover
  public ExpenseSuggestion recoverSuggestExpenseFromEmail(
      Exception e, String subject, String body) {
    log.error(
        "Email parsing failed for subject '{}' after all retries: {}", subject, e.getMessage());
    throw new RuntimeException("Email parsing failed after retries", e);
  }
}
