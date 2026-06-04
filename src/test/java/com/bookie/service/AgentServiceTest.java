package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

  @Mock private CopilotLlmService copilotLlmService;

  private AgentService service;

  @BeforeEach
  void setUp() {
    service = new AgentService(copilotLlmService);
    ReflectionTestUtils.setField(service, "agentModel", "test-agent-model");
  }

  @Nested
  class ProcessExpenseMessage {

    @Test
    void returnsTextReplyWhenModelRespondsWithText() {
      when(copilotLlmService.completeText(any(CopilotTextRequest.class)))
          .thenReturn("I can help you track that expense.");

      AgentService.AgentResponse response = service.processExpenseMessage("Track my expense");

      assertThat(response.message()).isEqualTo("I can help you track that expense.");
      assertThat(response.createdExpense()).isNull();
    }

    @Test
    void includesUserMessageInCopilotPrompt() {
      ArgumentCaptor<CopilotTextRequest> requestCaptor =
          ArgumentCaptor.forClass(CopilotTextRequest.class);
      when(copilotLlmService.completeText(requestCaptor.capture())).thenReturn("ok");

      service.processExpenseMessage("Record my Home Depot expense");

      CopilotTextRequest captured = requestCaptor.getValue();
      assertThat(captured.userPrompt()).isEqualTo("Record my Home Depot expense");
      assertThat(captured.model()).isEqualTo("test-agent-model");
      assertThat(captured.systemPrompt()).contains("Available categories");
    }

    @Test
    void neverCreatesExpenseDirectly() {
      when(copilotLlmService.completeText(any(CopilotTextRequest.class)))
          .thenReturn("Need details first.");

      AgentService.AgentResponse response = service.processExpenseMessage("Hello");

      assertThat(response.createdExpense()).isNull();
      verify(copilotLlmService).completeText(any(CopilotTextRequest.class));
    }
  }
}
