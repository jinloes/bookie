package com.bookie.service;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.integrations.llm.LlmGateway;
import com.bookie.integrations.llm.LlmTextRequest;
import com.bookie.model.AgentExpenseExtraction;
import com.bookie.model.FinancialCategory;
import com.bookie.model.TransactionDirection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Extracts a proposed cashflow record from a freeform chat message, then resolves classification
 * and counterparty data from stored records.
 *
 * <p>This never writes to the database. Earlier versions of this page implied an expense was
 * created directly from chat with no review step; that was a trust risk (a misheard amount or wrong
 * category became a committed record with no undo). The caller (frontend Agent page) must show the
 * {@link ProposedTransaction} to the user and call the normal income or expense endpoint only after
 * explicit confirmation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

  private final LlmGateway llmGateway;
  private final ObjectMapper objectMapper;
  private final CounterpartyCatalog counterpartyCatalog;
  private final AutomatedIntakeClassificationService classificationService;

  @Value("${ai.model.agent}")
  private String agentModel;

  private static final String SYSTEM_PROMPT =
      """
Extract a proposed household cashflow record from a freeform description. Today is %1$s.

Extract the following fields:
- direction: INCOME when money was received; EXPENSE when money was paid out.
- amount: the dollar amount as a number. If it cannot be determined, use 0.
- description: a short factual description of the transaction.
- date: ISO 8601 (YYYY-MM-DD). If not specified, use today's date.
- counterpartyName: the employer, customer, tenant, vendor, or reimbursing organization. \
Leave empty string ("") if not mentioned.
- needsMoreInfo: true only if the amount could not be determined at all (0) and \
the message doesn't already look like a follow-up answer.
- followUpQuestion: if needsMoreInfo is true, a short question asking for the \
missing amount. Otherwise empty string ("").

Do not output an activity, owner, property, category, or tax treatment. Those fields are \
resolved from stored application data after extraction.

Output ONLY the JSON object — no markdown fences, no preamble, no explanation. \
The first character must be { and the last must be }:
{"direction":"","amount":0,"description":"","date":"","counterpartyName":"","needsMoreInfo":false,"followUpQuestion":""}
""";

  public AgentResponse processExpenseMessage(String userMessage) {
    return processMessage(userMessage, TransactionDirection.EXPENSE);
  }

  public AgentResponse processMessage(String userMessage) {
    return processMessage(userMessage, null);
  }

  private AgentResponse processMessage(
      String userMessage, TransactionDirection requestedDirection) {
    LocalDate today = LocalDate.now();
    long start = System.currentTimeMillis();
    String json =
        llmGateway.completeText(
            LlmTextRequest.builder()
                .model(agentModel)
                .systemPrompt(SYSTEM_PROMPT.formatted(today))
                .userPrompt(userMessage)
                .build());
    log.info("LLM [agent]: {}ms", System.currentTimeMillis() - start);

    AgentExpenseExtraction extraction = parse(json);
    if (extraction == null) {
      return new AgentResponse(
          "I couldn't understand that. Could you describe the transaction again, including the "
              + "amount?",
          null,
          null);
    }
    if (extraction.needsMoreInfo() || extraction.amount() == null || extraction.amount() <= 0) {
      TransactionDirection direction =
          requestedDirection != null
              ? requestedDirection
              : extraction.direction() != null
                  ? extraction.direction()
                  : TransactionDirection.EXPENSE;
      String followUp =
          StringUtils.defaultIfBlank(
              extraction.followUpQuestion(),
              direction == TransactionDirection.INCOME
                  ? "What was the dollar amount for this income?"
                  : "What was the dollar amount for this expense?");
      return new AgentResponse(followUp, null, null);
    }

    ProposedTransaction proposed =
        toProposedTransaction(extraction, today, userMessage, requestedDirection);
    return new AgentResponse(
        "I found this transaction — review the details below and save it if it looks right.",
        proposed,
        proposed.direction() == TransactionDirection.EXPENSE ? proposed : null);
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

  private ProposedTransaction toProposedTransaction(
      AgentExpenseExtraction extraction,
      LocalDate today,
      String userMessage,
      TransactionDirection requestedDirection) {
    LocalDate date = parseDate(extraction.date(), today);
    TransactionDirection direction =
        requestedDirection != null
            ? requestedDirection
            : extraction.direction() != null
                ? extraction.direction()
                : TransactionDirection.EXPENSE;
    Counterparty payer = resolvePayer(extraction.counterpartyName());
    AutomatedIntakeClassificationService.Resolution classification =
        classificationService.resolveFromFreeform(
            direction, userMessage, extraction.counterpartyName(), date);
    FinancialActivity activity = classification.activity();
    FinancialCategory category = classification.category();
    return ProposedTransaction.builder()
        .direction(direction)
        .amount(BigDecimal.valueOf(extraction.amount()))
        .description(
            StringUtils.defaultIfBlank(
                extraction.description(),
                direction == TransactionDirection.INCOME ? "Income" : "Expense"))
        .date(date)
        .activityId(activity.getId())
        .activityName(activity.getName())
        .ownerName(activity.getOwner() != null ? activity.getOwner().getName() : null)
        .categoryId(category.getId())
        .categoryKey(category.getKey())
        .categoryLabel(category.getLabel())
        .propertyId(activity.getProperty() != null ? activity.getProperty().getId() : null)
        .propertyName(activity.getProperty() != null ? activity.getProperty().getName() : null)
        .payerId(payer != null ? payer.getId() : null)
        .counterpartyName(StringUtils.trimToNull(extraction.counterpartyName()))
        .classificationAmbiguous(classification.classificationAmbiguous())
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

  private Counterparty resolvePayer(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    List<Counterparty> payers = counterpartyCatalog.findAll();
    return payers.stream()
        .filter(
            p ->
                p.getName().equalsIgnoreCase(name.trim())
                    || CollectionUtils.emptyIfNull(p.getAliases()).stream()
                        .anyMatch(alias -> alias.equalsIgnoreCase(name.trim())))
        .findFirst()
        .orElse(null);
  }

  public record AgentResponse(
      String message,
      ProposedTransaction proposedTransaction,
      ProposedTransaction proposedExpense) {}

  @Builder
  public record ProposedTransaction(
      TransactionDirection direction,
      BigDecimal amount,
      String description,
      LocalDate date,
      Long activityId,
      String activityName,
      String ownerName,
      Long categoryId,
      String categoryKey,
      String categoryLabel,
      Long propertyId,
      String propertyName,
      Long payerId,
      String counterpartyName,
      boolean classificationAmbiguous) {}
}
