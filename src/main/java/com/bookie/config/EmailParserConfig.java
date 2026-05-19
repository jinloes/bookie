package com.bookie.config;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailParserConfig {

  // Explicit so every chat request carries the exact model name we pre-loaded via `lms load`.
  // Otherwise the request can ship without a model field and LM Studio (with JIT loading enabled)
  // may auto-load a separate model alongside the one already in memory.
  @Value("${spring.ai.openai.chat.options.model}")
  private String chatModel;

  @Bean
  public ChatClient emailParserChatClient(ChatClient.Builder builder) {
    return builder
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .defaultOptions(
            OpenAiChatOptions.builder()
                .model(chatModel)
                .extraBody(Map.of("enable_thinking", false))
                .build())
        .build();
  }
}
