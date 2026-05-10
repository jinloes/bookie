package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class LmStudioWarmupRunnerTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient chatClient;

  private LmStudioWarmupRunner runner;

  @BeforeEach
  void setUp() {
    runner = new LmStudioWarmupRunner(chatClient);
  }

  @Nested
  class WarmUp {

    @Test
    void sendsRequestToChatClient() {
      when(chatClient.prompt().system(anyString()).messages(anyList()).call().content())
          .thenReturn("ok");

      runner.warmUp();

      verify(chatClient.prompt().system(anyString()).messages(anyList()).call()).content();
    }

    @Test
    void doesNotThrowWhenLmStudioIsUnavailable() {
      when(chatClient.prompt().system(anyString()).messages(anyList()).call().content())
          .thenThrow(new RuntimeException("connection refused"));

      assertThatNoException().isThrownBy(() -> runner.warmUp());
    }
  }
}
