package com.bookie;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.integrations.llm.CopilotLlmService;
import com.bookie.integrations.llm.LlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"ai.provider=copilot"})
class CopilotProviderContextTest {

  @Autowired private LlmGateway llmGateway;

  @Test
  void wiresCopilotGateway() {
    assertThat(llmGateway).isInstanceOf(CopilotLlmService.class);
  }
}
