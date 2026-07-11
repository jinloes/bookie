package com.bookie.service;

import com.bookie.model.AgentExpenseExtraction;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.Payer;
import com.bookie.model.Property;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Extracts a proposed expense from a freeform chat message using the AI model, then resolves
 * property/payer names against existing records.
 *
 * <p>This never writes to the database. Earlier versions of this page implied an expense was
 * created directly from chat with no review step; that was a trust risk (a misheard amount or wrong
 * category became a committed record with no undo). The caller (frontend Agent page) must show the
 * {@link ProposedExpense} to the user and call the normal {@code POST /api/expenses} endpoint only
 * after explicit confirmation — identical to filling out the expense form by hand.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

  private final LlmGateway llmGateway;
  private final ObjectMapper objectMapper;
  private final PropertyRepository propertyRepository;
  private final PayerRepository payerRepository;

  @Value("${ai.model.agent}")
  private String agentModel;

  private static final String SYSTEM_PROMPT =
      """
          You are a helpful assistant for capturing rental property expenses from a freeform \
          description. Today is %1$s.

          Extract the following fields:
          - amount: the dollar amount as a number. If it cannot be determined, use 0.
          - description: a short description of what was paid for.
          - date: ISO 8601 (YYYY-MM-DD). If not specified, use today's date.
          - category: best-guess IRS Schedule E category using an exact enum key: %2$s. \
          Leave empty string ("") if it cannot be determined.
          - propertyName: the property name or address mentioned, exactly as written. \
          Leave empty string ("") if not mentioned.
          - payerName: the vendor or payee mentioned, exactly as written. \
          Leave empty string ("") if not mentioned.
          - needsMoreInfo: true only if the amount could not be determined at all (0) and \
          the message doesn't already look like a follow-up answer.
          - followUpQuestion: if needsMoreInfo is true, a short question asking for the \
          missing amount. Otherwise empty string ("").

          Output ONLY the JSON object — no markdown fences, no preamble, no explanation. \
          The first character must be { and the last must be }:
          {"amount":0,"description":"","date":"","category":"","propertyName":"","payerName":"","needsMoreInfo":false,"followUpQuestion":""}
          """;

  private static final String CATEGORY_LIST =
      String.join(", ", Arrays.stream(ExpenseCategory.values()).map(Enum::name).toList());

  public AgentResponse processExpenseMessage(String userMessage) {
    LocalDate today = LocalDate.now();
    long start = System.currentTimeMillis();
    String json =
        llmGateway.completeText(
            LlmTextRequest.builder()
                .model(agentModel)
                .systemPrompt(SYSTEM_PROMPT.formatted(today, CATEGORY_LIST))
                .userPrompt(userMessage)
                .build());
    log.info("LLM [agent]: {}ms", System.currentTimeMillis() - start);

    AgentExpenseExtraction extraction = parse(json);
    if (extraction == null) {
      return new AgentResponse(
          "I couldn't understand that. Could you describe the expense again, including the "
              + "amount?",
          null);
    }
    if (extraction.needsMoreInfo() || extraction.amount() == null || extraction.amount() <= 0) {
      String followUp =
          StringUtils.defaultIfBlank(
              extraction.followUpQuestion(), "What was the dollar amount for this expense?");
      return new AgentResponse(followUp, null);
    }

    ProposedExpense proposed = toProposedExpense(extraction, today);
    return new AgentResponse(
        "I found this expense — review the details below and save it if it looks right.", proposed);
  }

  private AgentExpenseExtraction parse(String json) {
    if (StringUtils.isBlank(json)) {
      log.warn("Agent model returned empty response");
      return null;
    }
    try {
      return objectMapper.readValue(json, AgentExpenseExtraction.class);
    } catch (JsonProcessingException e) {
      log.warn("Agent model returned invalid JSON: {}", json, e);
      return null;
    }
  }

  private ProposedExpense toProposedExpense(AgentExpenseExtraction extraction, LocalDate today) {
    LocalDate date = parseDate(extraction.date(), today);
    ExpenseCategory category = parseCategory(extraction.category());
    Property property = resolveProperty(extraction.propertyName());
    Payer payer = resolvePayer(extraction.payerName());
    return ProposedExpense.builder()
        .amount(BigDecimal.valueOf(extraction.amount()))
        .description(StringUtils.defaultIfBlank(extraction.description(), "Expense"))
        .date(date)
        .category(category)
        .propertyId(property != null ? property.getId() : null)
        .propertyName(StringUtils.trimToNull(extraction.propertyName()))
        .payerId(payer != null ? payer.getId() : null)
        .payerName(StringUtils.trimToNull(extraction.payerName()))
        .build();
  }

  private LocalDate parseDate(String raw, LocalDate fallback) {
    if (StringUtils.isBlank(raw)) {
      return fallback;
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException e) {
      return fallback;
    }
  }

  private ExpenseCategory parseCategory(String raw) {
    if (StringUtils.isBlank(raw)) {
      return null;
    }
    try {
      return ExpenseCategory.valueOf(raw.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Property resolveProperty(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    List<Property> properties = propertyRepository.findAll();
    return properties.stream()
        .filter(
            p ->
                p.getName().equalsIgnoreCase(name.trim())
                    || (p.getAddress() != null
                        && StringUtils.containsIgnoreCase(name, p.getAddress())))
        .findFirst()
        .orElse(null);
  }

  private Payer resolvePayer(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    List<Payer> payers = payerRepository.findAll();
    return payers.stream()
        .filter(
            p ->
                p.getName().equalsIgnoreCase(name.trim())
                    || p.getAliases().stream().anyMatch(a -> a.equalsIgnoreCase(name.trim())))
        .findFirst()
        .orElse(null);
  }

  public record AgentResponse(String message, ProposedExpense proposedExpense) {}

  @Builder
  public record ProposedExpense(
      BigDecimal amount,
      String description,
      LocalDate date,
      ExpenseCategory category,
      Long propertyId,
      String propertyName,
      Long payerId,
      String payerName) {}
}
