package com.bookie.config;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OcrConfig {

  @Value("${spring.ai.openai.ocr.model:qwen/qwen3.6-35b-a3}")
  private String visionModel;

  @Bean
  public ChatClient ocrChatClient(ChatClient.Builder builder) {
    return builder
        .defaultOptions(
            OpenAiChatOptions.builder()
                .model(visionModel)
                .extraBody(Map.of("enable_thinking", false))
                .build())
        .build();
  }
}
