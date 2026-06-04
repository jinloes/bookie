package com.bookie.service;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import java.time.LocalDate;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

  private final CopilotLlmService copilotLlmService;

  @Value("${ai.model.agent:${ai.model.chat:gpt-4.1}}")
  private String agentModel;

  private static final String SYSTEM_PROMPT =
      """
          You are a helpful assistant for managing rental property expenses.
          Help users capture expense details by asking concise follow-up questions when needed.
          Extract and reason about amount, description, category, date, and property name from the user's message.
          If the date is not specified, use today's date (%s).
          If the property is not specified, use "General".
          Choose the most appropriate category from the available options.
          Extract the vendor or payee name from the message if present.
          If the amount cannot be determined from the message, ask the user for it.
          """;

  private static final String CATEGORY_LIST =
      String.join(", ", Arrays.stream(ExpenseCategory.values()).map(Enum::name).toList());

  public AgentResponse processExpenseMessage(String userMessage) {
    LocalDate today = LocalDate.now();
    long start = System.currentTimeMillis();
    String response =
        copilotLlmService.completeText(
            CopilotTextRequest.builder()
                .model(agentModel)
                .systemPrompt(
                    SYSTEM_PROMPT.formatted(today) + "\nAvailable categories: " + CATEGORY_LIST)
                .userPrompt(userMessage)
                .build());
    log.info("LLM [agent]: {}ms", System.currentTimeMillis() - start);
    return new AgentResponse(response, null);
  }

  public record AgentResponse(String message, Expense createdExpense) {}
}
