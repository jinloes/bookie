package com.bookie;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.integrations.llm.LlmGateway;
import com.bookie.integrations.llm.SpringAiLlmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "ai.provider=spring-ai",
      "spring.ai.openai.api-key=test-key",
      "spring.autoconfigure.exclude="
          + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
          + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
          + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
          + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
          + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration"
    })
class SpringAiProviderContextTest {

  @Autowired private LlmGateway llmGateway;

  @Test
  void wiresSpringAiGateway() {
    assertThat(llmGateway).isInstanceOf(SpringAiLlmService.class);
  }
}
