package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OcrConfigTest {

  @Mock private ChatClient.Builder builder;
  @Mock private ChatClient chatClient;

  @Test
  void ocrChatClient_setsConfiguredVisionModelAsDefaultOption() {
    OcrConfig config = new OcrConfig();
    ReflectionTestUtils.setField(config, "visionModel", "qwen/qwen3.6-35b-a3");
    when(builder.defaultOptions(any(OpenAiChatOptions.class))).thenReturn(builder);
    when(builder.build()).thenReturn(chatClient);

    ChatClient result = config.ocrChatClient(builder);

    ArgumentCaptor<OpenAiChatOptions> optionsCaptor =
        ArgumentCaptor.forClass(OpenAiChatOptions.class);
    verify(builder).defaultOptions(optionsCaptor.capture());
    assertThat(optionsCaptor.getValue().getModel()).isEqualTo("qwen/qwen3.6-35b-a3");
    assertThat(result).isSameAs(chatClient);
  }
}
