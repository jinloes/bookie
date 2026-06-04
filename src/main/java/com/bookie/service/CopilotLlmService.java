package com.bookie.service;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.BlobAttachment;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CopilotLlmService {

  private final CopilotClient copilotClient;
  private final Object lifecycleLock = new Object();

  @Value("${ai.request-timeout-ms:180000}")
  private long requestTimeoutMs;

  private volatile boolean started;

  public CopilotLlmService(CopilotClient copilotClient) {
    this.copilotClient = copilotClient;
  }

  public String completeText(CopilotTextRequest request) {
    MessageOptions options = new MessageOptions().setPrompt(request.userPrompt());
    return complete(request.model(), request.systemPrompt(), options);
  }

  public String completeVision(CopilotVisionRequest request) {
    BlobAttachment attachment =
        new BlobAttachment()
            .setData(Base64.getEncoder().encodeToString(request.binaryData()))
            .setMimeType(request.mimeType())
            .setDisplayName(request.displayName());
    MessageOptions options =
        new MessageOptions().setPrompt(request.userPrompt()).setAttachments(List.of(attachment));
    return complete(request.model(), request.systemPrompt(), options);
  }

  private String complete(String model, String systemPrompt, MessageOptions options) {
    ensureClientStarted();
    long start = System.currentTimeMillis();
    try (CopilotSession session =
        copilotClient
            .createSession(buildSessionConfig(model, systemPrompt))
            .get(requestTimeoutMs, TimeUnit.MILLISECONDS)) {
      AssistantMessageEvent event = session.sendAndWait(options, requestTimeoutMs).get();
      String content = event != null && event.getData() != null ? event.getData().content() : null;
      if (StringUtils.isBlank(content)) {
        throw new IllegalStateException("AI response was empty");
      }
      return content;
    } catch (Exception e) {
      throw new RuntimeException("AI service request failed", e);
    } finally {
      log.info("LLM [assistant]: {}ms", System.currentTimeMillis() - start);
    }
  }

  private void ensureClientStarted() {
    if (started) {
      return;
    }
    synchronized (lifecycleLock) {
      if (started) {
        return;
      }
      try {
        copilotClient.start().get(requestTimeoutMs, TimeUnit.MILLISECONDS);
        started = true;
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize AI service", e);
      }
    }
  }

  private SessionConfig buildSessionConfig(String model, String systemPrompt) {
    return new SessionConfig()
        .setModel(model)
        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
        .setSystemMessage(
            new SystemMessageConfig().setMode(SystemMessageMode.APPEND).setContent(systemPrompt));
  }
}
