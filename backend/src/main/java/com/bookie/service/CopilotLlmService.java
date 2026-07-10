package com.bookie.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.generated.ExternalToolRequestedEvent;
import com.github.copilot.generated.ToolExecutionCompleteEvent;
import com.github.copilot.generated.ToolExecutionStartEvent;
import com.github.copilot.rpc.BlobAttachment;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import com.github.copilot.rpc.ToolDefinition;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "copilot", matchIfMissing = true)
public class CopilotLlmService implements LlmGateway {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final CopilotClient copilotClient;
  private final CopilotToolEventTrace toolEventTrace;
  private final ObjectMapper objectMapper;
  private final Object lifecycleLock = new Object();

  @Value("${ai.request-timeout-ms:180000}")
  private long requestTimeoutMs;

  @Value("${ai.tools.trace-events:false}")
  private boolean traceToolEvents;

  private volatile boolean started;

  public CopilotLlmService(
      CopilotClient copilotClient,
      CopilotToolEventTrace toolEventTrace,
      ObjectMapper objectMapper) {
    this.copilotClient = copilotClient;
    this.toolEventTrace = toolEventTrace;
    this.objectMapper = objectMapper;
  }

  @Override
  public String completeText(LlmTextRequest request) {
    MessageOptions options = new MessageOptions().setPrompt(request.userPrompt());
    return complete(
        request.model(),
        request.systemPrompt(),
        request.tools() == null ? List.of() : toCopilotTools(request.tools()),
        options);
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
    return complete(request.model(), request.systemPrompt(), List.of(), options);
  }

  private String complete(
      String model, String systemPrompt, List<ToolDefinition> tools, MessageOptions options) {
    ensureClientStarted();
    long start = System.currentTimeMillis();
    try (CopilotSession session =
        copilotClient
            .createSession(buildSessionConfig(model, systemPrompt, tools))
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

  private SessionConfig buildSessionConfig(
      String model, String systemPrompt, List<ToolDefinition> tools) {
    SessionConfig sessionConfig =
        new SessionConfig()
            .setModel(model)
            .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
            .setSystemMessage(
                new SystemMessageConfig()
                    .setMode(SystemMessageMode.APPEND)
                    .setContent(systemPrompt));
    if (tools != null && !tools.isEmpty()) {
      sessionConfig.setTools(tools);
    }
    if (traceToolEvents) {
      sessionConfig.setOnEvent(
          event -> {
            if (event instanceof ToolExecutionStartEvent toolStart && toolStart.getData() != null) {
              toolEventTrace.recordToolStart(toolStart.getData().toolName());
            } else if (event instanceof ExternalToolRequestedEvent requested
                && requested.getData() != null) {
              toolEventTrace.recordToolStart(requested.getData().toolName());
            } else if (event instanceof ToolExecutionCompleteEvent complete
                && complete.getData() != null
                && complete.getData().toolDescription() != null) {
              toolEventTrace.recordToolStart(complete.getData().toolDescription().name());
            }
          });
    }
    return sessionConfig;
  }

  private List<ToolDefinition> toCopilotTools(List<LlmToolDefinition> tools) {
    return tools.stream()
        .map(
            tool ->
                ToolDefinition.create(
                    tool.name(),
                    tool.description(),
                    tool.parameters(),
                    invocation ->
                        java.util.concurrent.CompletableFuture.completedFuture(
                            tool.handler()
                                .apply(
                                    objectMapper.convertValue(
                                        invocation.getArguments(), MAP_TYPE)))))
        .toList();
  }
}
