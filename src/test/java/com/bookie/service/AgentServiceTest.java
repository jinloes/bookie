package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.bookie.repository.PropertyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AnthropicClient anthropicClient;

  @Mock private ExpenseService expenseService;
  @Mock private PayerService payerService;
  @Mock private PropertyRepository propertyRepository;

  private AgentService service;

  @BeforeEach
  void setUp() {
    service = new AgentService(anthropicClient, expenseService, payerService, propertyRepository);
  }

  @Nested
  class ProcessExpenseMessage {

    @Test
    void returnsTextReplyWhenModelRespondsWithText() {
      TextBlock textBlock = mock(TextBlock.class);
      when(textBlock.text()).thenReturn("I can help you track that expense.");

      ContentBlock block = mock(ContentBlock.class);
      when(block.isText()).thenReturn(true);
      when(block.asText()).thenReturn(textBlock);

      Message message = mock(Message.class);
      when(message.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));
      when(message.content()).thenReturn(List.of(block));
      when(anthropicClient.messages().create(any(MessageCreateParams.class))).thenReturn(message);

      AgentService.AgentResponse response = service.processExpenseMessage("Track my expense");

      assertThat(response.message()).isEqualTo("I can help you track that expense.");
      assertThat(response.createdExpense()).isNull();
    }

    @Test
    void returnsEmptyReplyWhenContentHasNoTextBlocks() {
      ContentBlock block = mock(ContentBlock.class);
      when(block.isText()).thenReturn(false);

      Message message = mock(Message.class);
      when(message.stopReason()).thenReturn(Optional.empty());
      when(message.content()).thenReturn(List.of(block));
      when(anthropicClient.messages().create(any(MessageCreateParams.class))).thenReturn(message);

      AgentService.AgentResponse response = service.processExpenseMessage("Hello");

      assertThat(response.message()).isEmpty();
      assertThat(response.createdExpense()).isNull();
    }

    @Test
    void joinsMultipleTextBlocksWithSpace() {
      ContentBlock block1 = mock(ContentBlock.class);
      TextBlock text1 = mock(TextBlock.class);
      when(text1.text()).thenReturn("Part one.");
      when(block1.isText()).thenReturn(true);
      when(block1.asText()).thenReturn(text1);

      ContentBlock block2 = mock(ContentBlock.class);
      TextBlock text2 = mock(TextBlock.class);
      when(text2.text()).thenReturn("Part two.");
      when(block2.isText()).thenReturn(true);
      when(block2.asText()).thenReturn(text2);

      Message message = mock(Message.class);
      when(message.stopReason()).thenReturn(Optional.empty());
      when(message.content()).thenReturn(List.of(block1, block2));
      when(anthropicClient.messages().create(any(MessageCreateParams.class))).thenReturn(message);

      AgentService.AgentResponse response = service.processExpenseMessage("Hello");

      assertThat(response.message()).isEqualTo("Part one. Part two.");
    }

    @Test
    void doesNotCallExpenseServiceWhenNoToolUse() {
      ContentBlock block = mock(ContentBlock.class);
      when(block.isText()).thenReturn(false);

      Message message = mock(Message.class);
      when(message.stopReason()).thenReturn(Optional.empty());
      when(message.content()).thenReturn(List.of(block));
      when(anthropicClient.messages().create(any(MessageCreateParams.class))).thenReturn(message);

      service.processExpenseMessage("Hello");

      verify(expenseService, never()).save(any());
    }
  }
}
