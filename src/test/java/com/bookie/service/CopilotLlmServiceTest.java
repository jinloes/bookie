package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.BlobAttachment;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.SessionConfig;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CopilotLlmServiceTest {

  @Mock private CopilotClient copilotClient;
  @Mock private CopilotSession copilotSession;
  @Mock private AssistantMessageEvent assistantMessageEvent;
  @Mock private AssistantMessageEvent.AssistantMessageEventData assistantMessageData;

  private CopilotLlmService service;

  @BeforeEach
  void setUp() throws Exception {
    service = new CopilotLlmService(copilotClient);
    ReflectionTestUtils.setField(service, "requestTimeoutMs", 5_000L);

    when(copilotClient.start()).thenReturn(CompletableFuture.completedFuture(null));
    when(copilotClient.createSession(any(SessionConfig.class)))
        .thenReturn(CompletableFuture.completedFuture(copilotSession));
    when(copilotSession.sendAndWait(any(MessageOptions.class), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(assistantMessageEvent));
    when(assistantMessageEvent.getData()).thenReturn(assistantMessageData);
    when(assistantMessageData.content()).thenReturn("{\"ok\":true}");
  }

  @Nested
  class CompleteText {

    @Test
    void returnsAssistantContent() {
      String result =
          service.completeText(
              CopilotTextRequest.builder()
                  .model("gpt-4.1")
                  .systemPrompt("system")
                  .userPrompt("user")
                  .build());

      assertThat(result).isEqualTo("{\"ok\":true}");
    }

    @Test
    void startsClientOnlyOnceAcrossCalls() {
      service.completeText(
          CopilotTextRequest.builder()
              .model("gpt-4.1")
              .systemPrompt("system")
              .userPrompt("user-1")
              .build());
      service.completeText(
          CopilotTextRequest.builder()
              .model("gpt-4.1")
              .systemPrompt("system")
              .userPrompt("user-2")
              .build());

      verify(copilotClient, times(1)).start();
      verify(copilotClient, times(2)).createSession(any(SessionConfig.class));
    }

    @Test
    void blankAssistantContentThrows() {
      when(assistantMessageData.content()).thenReturn(" ");

      assertThatThrownBy(
              () ->
                  service.completeText(
                      CopilotTextRequest.builder()
                          .model("gpt-4.1")
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
    void sendsImageAsBlobAttachment() {
      byte[] imageBytes = "png-bytes".getBytes(StandardCharsets.UTF_8);
      ArgumentCaptor<MessageOptions> optionsCaptor = ArgumentCaptor.forClass(MessageOptions.class);

      service.completeVision(
          CopilotVisionRequest.builder()
              .model("gpt-4.1")
              .systemPrompt("ocr-system")
              .userPrompt("ocr-user")
              .mimeType("image/png")
              .displayName("page-1.png")
              .binaryData(imageBytes)
              .build());

      verify(copilotSession).sendAndWait(optionsCaptor.capture(), anyLong());
      List<?> attachments = optionsCaptor.getValue().getAttachments();
      assertThat(attachments).hasSize(1);
      BlobAttachment blob = (BlobAttachment) attachments.get(0);
      assertThat(blob.getMimeType()).isEqualTo("image/png");
      assertThat(blob.getDisplayName()).isEqualTo("page-1.png");
      assertThat(blob.getData()).isEqualTo(Base64.getEncoder().encodeToString(imageBytes));
    }
  }
}
