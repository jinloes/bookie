package com.bookie.service;

import com.bookie.model.ExpenseSuggestion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class EmailParserService {

    private record EmailParseResult(
            Double amount,
            String description,
            String date,
            String category,
            String propertyName,
            String payerName) {}

    private static final String SYSTEM_PROMPT = """
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

    public ExpenseSuggestion suggestExpenseFromEmail(String subject, String body) {
        String systemPrompt = SYSTEM_PROMPT.formatted(LocalDate.now());
        EmailParseResult result = chatClient.prompt()
                .system(systemPrompt)
                .user("Subject: %s\n\n%s".formatted(subject, body))
                .tools(tools)
                .call()
                .entity(EmailParseResult.class);

        return new ExpenseSuggestion(Objects.requireNonNull(result).amount(), result.description(), result.date(),
                result.category(), result.propertyName(), result.payerName(), null, null);
    }
}