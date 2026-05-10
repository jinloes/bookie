package com.bookie.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.repository.PropertyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

  private final AnthropicClient anthropicClient;
  private final ExpenseService expenseService;
  private final PayerService payerService;
  private final PropertyRepository propertyRepository;

  private static final String SYSTEM_PROMPT =
      """
          You are a helpful assistant for managing rental property expenses.
          When a user describes an expense, use the create_expense tool to record it.
          Extract the amount, description, category, date, and property name from the user's message.
          If the date is not specified, use today's date (%s).
          If the property is not specified, use "General".
          Choose the most appropriate category from the available options.
          Extract the vendor or payee name from the message if present.
          If the amount cannot be determined from the message, ask the user for it before calling the tool.
          """;

  private static final List<String> CATEGORIES =
      Arrays.stream(ExpenseCategory.values()).map(Enum::name).toList();

  private static final Tool CREATE_EXPENSE_TOOL =
      Tool.builder()
          .name("create_expense")
          .description(
              "Use this tool when the user has described an expense to record. "
                  + "Call it after extracting amount, description, category, date, and property from the user's message. "
                  + "Do not call this tool if the user is asking a question rather than recording an expense.")
          .inputSchema(
              Tool.InputSchema.builder()
                  .properties(
                      Tool.InputSchema.Properties.builder()
                          .putAdditionalProperty(
                              "amount",
                              JsonValue.from(
                                  Map.of(
                                      "type", "number",
                                      "description", "The expense amount in dollars")))
                          .putAdditionalProperty(
                              "description",
                              JsonValue.from(
                                  Map.of(
                                      "type", "string",
                                      "description", "A brief description of the expense")))
                          .putAdditionalProperty(
                              "category",
                              JsonValue.from(
                                  Map.of(
                                      "type", "string",
                                      "enum", CATEGORIES,
                                      "description", "The expense category")))
                          .putAdditionalProperty(
                              "date",
                              JsonValue.from(
                                  Map.of(
                                      "type", "string",
                                      "description",
                                          "The date of the expense in YYYY-MM-DD format")))
                          .putAdditionalProperty(
                              "propertyName",
                              JsonValue.from(
                                  Map.of(
                                      "type", "string",
                                      "description",
                                          "The name or identifier of the rental property")))
                          .putAdditionalProperty(
                              "payerName",
                              JsonValue.from(
                                  Map.of(
                                      "type", "string",
                                      "description",
                                          "The name of the person or company that was paid")))
                          .build())
                  .required(List.of("amount", "description", "category", "date", "propertyName"))
                  .build())
          .build();

  public AgentResponse processExpenseMessage(String userMessage) {
    LocalDate today = LocalDate.now();
    List<MessageParam> messages =
        List.of(MessageParam.builder().role(MessageParam.Role.USER).content(userMessage).build());

    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(Model.CLAUDE_SONNET_4_6)
            .maxTokens(512L)
            .system(SYSTEM_PROMPT.formatted(today))
            .addTool(CREATE_EXPENSE_TOOL)
            .messages(messages)
            .build();

    long start = System.currentTimeMillis();
    Message response = anthropicClient.messages().create(params);
    log.info("LLM [agent]: {}ms", System.currentTimeMillis() - start);

    Expense createdExpense = null;
    String agentReply = "";

    if (response.stopReason().map(r -> r == StopReason.TOOL_USE).orElse(false)) {
      for (ContentBlock block : response.content()) {
        if (block.isToolUse()) {
          ToolUseBlock toolUse = block.asToolUse();
          if ("create_expense".equals(toolUse.name())) {
            createdExpense = executeCreateExpense(toolUse);
          }
        }
      }

      if (createdExpense != null) {
        final Expense finalExpense = createdExpense;
        String toolResultContent =
            String.format(
                "Expense created successfully: %s - $%.2f on %s (Category: %s, Property: %s)",
                finalExpense.getDescription(),
                finalExpense.getAmount(),
                finalExpense.getDate(),
                finalExpense.getCategory(),
                finalExpense.getProperty() != null ? finalExpense.getProperty().getName() : "None");

        // Use toParam() to convert ToolUseBlock → ToolUseBlockParam for the round-trip
        List<ContentBlockParam> assistantContent =
            response.content().stream()
                .map(
                    block -> {
                      if (block.isText()) {
                        return ContentBlockParam.ofText(
                            TextBlockParam.builder().text(block.asText().text()).build());
                      } else if (block.isToolUse()) {
                        return ContentBlockParam.ofToolUse(block.asToolUse().toParam());
                      }
                      return ContentBlockParam.ofText(TextBlockParam.builder().text("").build());
                    })
                .toList();

        String toolUseId =
            response.content().stream()
                .filter(ContentBlock::isToolUse)
                .map(b -> b.asToolUse().id())
                .findFirst()
                .orElse("");

        ContentBlockParam toolResultParam =
            ContentBlockParam.ofToolResult(
                ToolResultBlockParam.builder()
                    .toolUseId(toolUseId)
                    .content(toolResultContent)
                    .build());

        MessageParam assistantMsg =
            MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .contentOfBlockParams(assistantContent)
                .build();

        MessageParam toolResultMsg =
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(List.of(toolResultParam))
                .build();

        MessageCreateParams followUpParams =
            MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_6)
                .maxTokens(256L)
                .system(SYSTEM_PROMPT.formatted(today))
                .addTool(CREATE_EXPENSE_TOOL)
                .messages(List.of(messages.get(0), assistantMsg, toolResultMsg))
                .build();

        long followUpStart = System.currentTimeMillis();
        Message followUp = anthropicClient.messages().create(followUpParams);
        log.info("LLM [agent follow-up]: {}ms", System.currentTimeMillis() - followUpStart);
        agentReply =
            followUp.content().stream()
                .filter(ContentBlock::isText)
                .map(b -> b.asText().text())
                .collect(Collectors.joining(" "));
      }
    } else {
      agentReply =
          response.content().stream()
              .filter(ContentBlock::isText)
              .map(b -> b.asText().text())
              .collect(Collectors.joining(" "));
    }

    return new AgentResponse(agentReply, createdExpense);
  }

  private Expense executeCreateExpense(ToolUseBlock toolUse) {
    // Convert the raw JsonValue input to a Map<String, Object> using Jackson
    Map<String, Object> input =
        toolUse._input().convert(new TypeReference<Map<String, Object>>() {});

    BigDecimal amount = new BigDecimal(input.get("amount").toString());
    String description = (String) input.getOrDefault("description", "");
    String categoryStr = (String) input.getOrDefault("category", "OTHER");
    String dateStr = (String) input.getOrDefault("date", LocalDate.now().toString());
    String propertyName = (String) input.getOrDefault("propertyName", "");
    String payerName = (String) input.get("payerName");

    Expense expense = new Expense();
    expense.setAmount(amount);
    expense.setDescription(description);
    expense.setCategory(parseCategory(categoryStr));
    expense.setDate(LocalDate.parse(dateStr));

    if (StringUtils.isNotBlank(propertyName)) {
      propertyRepository.findByNameIgnoreCase(propertyName).ifPresent(expense::setProperty);
    }

    if (StringUtils.isNotBlank(payerName)) {
      Payer payer =
          payerService
              .findByName(payerName)
              .orElseGet(
                  () ->
                      payerService.save(
                          Payer.builder().name(payerName).type(PayerType.COMPANY).build()));
      expense.setPayer(payer);
    }

    return expenseService.save(expense);
  }

  private ExpenseCategory parseCategory(String categoryStr) {
    try {
      return ExpenseCategory.valueOf(categoryStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      return ExpenseCategory.OTHER;
    }
  }

  public record AgentResponse(String message, Expense createdExpense) {}
}
