package com.bookie.integrations.llm;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.BlobAttachment;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.PermissionRequestResult;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CopilotLlmService implements LlmGateway {

  static final PermissionHandler DENY_ALL =
      (request, invocation) -> {
        log.warn("Refused AI permission request of kind {}", request.getKind());
        return CompletableFuture.completedFuture(
            PermissionRequestResult.reject("Bookie does not permit tool use"));
      };

  private final CopilotClient copilotClient;
  private final Object lifecycleLock = new Object();

  @Value("${ai.request-timeout-ms:180000}")
  private long requestTimeoutMs;

  private volatile boolean started;

  public CopilotLlmService(CopilotClient copilotClient) {
    this.copilotClient = copilotClient;
  }

  @Override
  public String completeText(LlmTextRequest request) {
    MessageOptions options = new MessageOptions().setPrompt(request.userPrompt());
    return complete(request.model(), request.systemPrompt(), options);
  }

  @Override
  public String completeVision(LlmVisionRequest request) {
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
      // Defensive timeout on the returned future in addition to the SDK's internal
      // requestTimeoutMs wait — protects against the AI subprocess hanging without ever
      // completing or rejecting the future, which would otherwise strand this worker thread.
      AssistantMessageEvent event =
          session
              .sendAndWait(options, requestTimeoutMs)
              .get(requestTimeoutMs, TimeUnit.MILLISECONDS);
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
    // Prompts carry untrusted email and receipt text, so built-in CLI tools, user MCP config
    // discovery, and every permission request are disabled.
    return new SessionConfig()
        .setModel(model)
        .setOnPermissionRequest(DENY_ALL)
        .setAvailableTools(List.of())
        .setEnableConfigDiscovery(false)
        .setSystemMessage(
            new SystemMessageConfig().setMode(SystemMessageMode.APPEND).setContent(systemPrompt));
  }
}
