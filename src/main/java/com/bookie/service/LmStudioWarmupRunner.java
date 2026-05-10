package com.bookie.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Sends a no-op request to LM Studio after startup to initialize the model's KV cache. */
@Slf4j
@Component
public class LmStudioWarmupRunner {

  private final ChatClient chatClient;

  public LmStudioWarmupRunner(@Qualifier("emailParserChatClient") ChatClient chatClient) {
    this.chatClient = chatClient;
  }

  @Async
  @EventListener(ApplicationReadyEvent.class)
  public void warmUp() {
    long start = System.currentTimeMillis();
    log.info("LM Studio warm-up: starting");
    try {
      chatClient
          .prompt()
          .system("You are a helpful assistant.")
          .messages(
              List.of(new UserMessage("Hi /no_think"), new AssistantMessage("<think>\n\n</think>")))
          .call()
          .content();
      log.info("LM Studio warm-up: done in {}ms", System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.warn("LM Studio warm-up failed (LM Studio may not be running): {}", e.getMessage());
    }
  }
}
