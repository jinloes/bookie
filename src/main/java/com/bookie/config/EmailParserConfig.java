package com.bookie.config;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailParserConfig {

  @Bean
  public ChatClient emailParserChatClient(ChatClient.Builder builder) {
    return builder
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .defaultOptions(
            OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
        .build();
  }
}
