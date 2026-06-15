package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.PromptUserSpec;
import org.springframework.ai.tool.ToolCallback;

@ExtendWith(MockitoExtension.class)
class SpringAiLlmServiceTest {

  @Mock private ChatClient.Builder chatClientBuilder;
  @Mock private ChatClient chatClient;
  @Mock private ChatClientRequestSpec requestSpec;
  @Mock private CallResponseSpec callResponseSpec;

  private SpringAiLlmService service;

  @BeforeEach
  void setUp() {
    service = new SpringAiLlmService(chatClientBuilder, new ObjectMapper());
    when(chatClientBuilder.build()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.options(any())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);
  }

  @Nested
  class CompleteText {

    @Test
    void returnsContent() {
      when(callResponseSpec.content()).thenReturn("{\"ok\":true}");
      when(requestSpec.user(anyString())).thenReturn(requestSpec);

      String result =
          service.completeText(
              LlmTextRequest.builder()
                  .model("gpt-5-mini")
                  .systemPrompt("system")
                  .userPrompt("user")
                  .tools(List.of())
                  .build());

      assertThat(result).isEqualTo("{\"ok\":true}");
    }

    @Test
    void attachesRuntimeToolsWhenProvided() {
      when(callResponseSpec.content()).thenReturn("{\"ok\":true}");
      when(requestSpec.user(anyString())).thenReturn(requestSpec);
      when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
      LlmToolDefinition tool =
          LlmToolDefinition.builder()
              .name("testTool")
              .description("Use this for test.")
              .parameters(Map.of("type", "object"))
              .handler(args -> Map.of("ok", true))
              .build();

      service.completeText(
          LlmTextRequest.builder()
              .model("gpt-5-mini")
              .systemPrompt("system")
              .userPrompt("user")
              .tools(List.of(tool))
              .build());

      ArgumentCaptor<ToolCallback[]> toolsCaptor = ArgumentCaptor.forClass(ToolCallback[].class);
      verify(requestSpec).toolCallbacks(toolsCaptor.capture());
      assertThat(toolsCaptor.getValue()).hasSize(1);
    }

    @Test
    void blankContentThrows() {
      when(callResponseSpec.content()).thenReturn(" ");
      when(requestSpec.user(anyString())).thenReturn(requestSpec);

      assertThatThrownBy(
              () ->
                  service.completeText(
                      LlmTextRequest.builder()
                          .model("gpt-5-mini")
                          .systemPrompt("system")
                          .userPrompt("user")
                          .build()))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("AI service request failed");
    }
  }

  @Nested
  class CompleteVision {

    @Test
    void usesPromptUserConsumerWithMediaAttachment() {
      when(callResponseSpec.content()).thenReturn("ocr text");
      when(requestSpec.user(org.mockito.ArgumentMatchers.<Consumer<PromptUserSpec>>any()))
          .thenReturn(requestSpec);

      String result =
          service.completeVision(
              LlmVisionRequest.builder()
                  .model("gpt-5-mini")
                  .systemPrompt("ocr-system")
                  .userPrompt("ocr-user")
                  .mimeType("image/png")
                  .displayName("page-1.png")
                  .binaryData(new byte[] {1, 2, 3})
                  .tools(List.of())
                  .build());

      assertThat(result).isEqualTo("ocr text");
      verify(requestSpec).user(org.mockito.ArgumentMatchers.<Consumer<PromptUserSpec>>any());
    }
  }
}
