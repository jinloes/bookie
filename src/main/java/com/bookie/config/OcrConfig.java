package com.bookie.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OcrConfig {

  @Value("${spring.ai.ollama.ocr.model:glm-ocr}")
  private String visionModel;

  @Bean
  public ChatClient ocrChatClient(ChatClient.Builder builder) {
    return builder.defaultOptions(OllamaChatOptions.builder().model(visionModel).build()).build();
  }
}
