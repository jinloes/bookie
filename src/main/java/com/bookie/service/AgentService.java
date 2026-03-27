package com.bookie.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final ExpenseService expenseService;

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant for managing rental property expenses.
            When a user describes an expense, use the create_expense tool to record it.
            Extract the amount, description, category, date, and property name from the user's message.
            If the date is not specified, use today's date (%s).
            If the property is not specified, use "General".
            Choose the most appropriate category from the available options.
            Always confirm what you created.
            """.formatted(LocalDate.now());

    private static final List<String> CATEGORIES = Arrays.stream(ExpenseCategory.values())
            .map(Enum::name)
            .collect(Collectors.toList());

    public AgentResponse processExpenseMessage(String userMessage) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();

        Tool createExpenseTool = Tool.builder()
                .name("create_expense")
                .description("Creates a rental expense record in the system")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("amount", JsonValue.from(Map.of(
                                        "type", "number",
                                        "description", "The expense amount in dollars"
                                )))
                                .putAdditionalProperty("description", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "A brief description of the expense"
                                )))
                                .putAdditionalProperty("category", JsonValue.from(Map.of(
                                        "type", "string",
                                        "enum", CATEGORIES,
                                        "description", "The expense category"
                                )))
                                .putAdditionalProperty("date", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "The date of the expense in YYYY-MM-DD format"
                                )))
                                .putAdditionalProperty("propertyName", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "The name or identifier of the rental property"
                                )))
                                .build())
                        .required(List.of("amount", "description", "category", "date", "propertyName"))
                        .build())
                .build();

        List<MessageParam> messages = List.of(
                MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(userMessage)
                        .build()
        );

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_6)
                .maxTokens(4096L)
                .system(SYSTEM_PROMPT)
                .addTool(createExpenseTool)
                .messages(messages)
                .build();

        Message response = client.messages().create(params);

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
                String toolResultContent = String.format(
                        "Expense created successfully: %s - $%.2f on %s (Category: %s, Property: %s)",
                        finalExpense.getDescription(), finalExpense.getAmount(),
                        finalExpense.getDate(), finalExpense.getCategory(), finalExpense.getPropertyName()
                );

                // Use toParam() to convert ToolUseBlock → ToolUseBlockParam for the round-trip
                List<ContentBlockParam> assistantContent = response.content().stream()
                        .map(block -> {
                            if (block.isText()) {
                                return ContentBlockParam.ofText(TextBlockParam.builder()
                                        .text(block.asText().text())
                                        .build());
                            } else if (block.isToolUse()) {
                                return ContentBlockParam.ofToolUse(block.asToolUse().toParam());
                            }
                            return ContentBlockParam.ofText(TextBlockParam.builder().text("").build());
                        })
                        .collect(Collectors.toList());

                String toolUseId = response.content().stream()
                        .filter(ContentBlock::isToolUse)
                        .map(b -> b.asToolUse().id())
                        .findFirst()
                        .orElse("");

                ContentBlockParam toolResultParam = ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(toolUseId)
                                .content(toolResultContent)
                                .build()
                );

                MessageParam assistantMsg = MessageParam.builder()
                        .role(MessageParam.Role.ASSISTANT)
                        .contentOfBlockParams(assistantContent)
                        .build();

                MessageParam toolResultMsg = MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .contentOfBlockParams(List.of(toolResultParam))
                        .build();

                MessageCreateParams followUpParams = MessageCreateParams.builder()
                        .model(Model.CLAUDE_OPUS_4_6)
                        .maxTokens(1024L)
                        .system(SYSTEM_PROMPT)
                        .addTool(createExpenseTool)
                        .messages(List.of(messages.get(0), assistantMsg, toolResultMsg))
                        .build();

                Message followUp = client.messages().create(followUpParams);
                agentReply = followUp.content().stream()
                        .filter(ContentBlock::isText)
                        .map(b -> b.asText().text())
                        .collect(Collectors.joining(" "));
            }
        } else {
            agentReply = response.content().stream()
                    .filter(ContentBlock::isText)
                    .map(b -> b.asText().text())
                    .collect(Collectors.joining(" "));
        }

        return new AgentResponse(agentReply, createdExpense);
    }

    private Expense executeCreateExpense(ToolUseBlock toolUse) {
        // Convert the raw JsonValue input to a Map<String, Object> using Jackson
        Map<String, Object> input = toolUse._input().convert(new TypeReference<Map<String, Object>>() {});

        double amount = ((Number) input.get("amount")).doubleValue();
        String description = (String) input.getOrDefault("description", "");
        String categoryStr = (String) input.getOrDefault("category", "OTHER");
        String dateStr = (String) input.getOrDefault("date", LocalDate.now().toString());
        String propertyName = (String) input.getOrDefault("propertyName", "General");

        Expense expense = new Expense();
        expense.setAmount(BigDecimal.valueOf(amount));
        expense.setDescription(description);
        expense.setCategory(parseCategory(categoryStr));
        expense.setDate(LocalDate.parse(dateStr));
        expense.setPropertyName(propertyName);

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
