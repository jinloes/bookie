package com.bookie.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "spring-ai")
public class SpringAiLlmService implements LlmGateway {

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final ChatClient.Builder chatClientBuilder;
  private final ObjectMapper objectMapper;

  @Override
  public String completeText(LlmTextRequest request) {
    try {
      ChatClient.ChatClientRequestSpec spec =
          createBaseRequestSpec(request.model(), request.systemPrompt());
      spec = spec.user(request.userPrompt());
      spec = applyTools(spec, request.tools());
      String content = spec.call().content();
      if (StringUtils.isBlank(content)) {
        throw new IllegalStateException("AI response was empty");
      }
      return content;
    } catch (Exception e) {
      throw new RuntimeException("AI service request failed", e);
    }
  }

  @Override
  public String completeVision(LlmVisionRequest request) {
    try {
      ChatClient.ChatClientRequestSpec spec =
          createBaseRequestSpec(request.model(), request.systemPrompt());
      MimeType mimeType = MimeTypeUtils.parseMimeType(request.mimeType());
      ByteArrayResource resource =
          new ByteArrayResource(request.binaryData()) {
            @Override
            public String getFilename() {
              return request.displayName();
            }
          };
      spec = spec.user(u -> u.text(request.userPrompt()).media(mimeType, resource));
      spec = applyTools(spec, request.tools());
      String content = spec.call().content();
      if (StringUtils.isBlank(content)) {
        throw new IllegalStateException("AI response was empty");
      }
      return content;
    } catch (Exception e) {
      throw new RuntimeException("AI service request failed", e);
    }
  }

  private ChatClient.ChatClientRequestSpec createBaseRequestSpec(
      String model, String systemPrompt) {
    ChatClient.ChatClientRequestSpec spec = chatClientBuilder.build().prompt().system(systemPrompt);
    if (StringUtils.isNotBlank(model)) {
      spec = spec.options(ChatOptions.builder().model(model).build());
    }
    return spec;
  }

  private ChatClient.ChatClientRequestSpec applyTools(
      ChatClient.ChatClientRequestSpec spec, List<LlmToolDefinition> tools) {
    if (tools == null || tools.isEmpty()) {
      return spec;
    }
    ToolCallback[] callbacks =
        tools.stream().map(this::toToolCallback).toArray(ToolCallback[]::new);
    return spec.toolCallbacks(callbacks);
  }

  private ToolCallback toToolCallback(LlmToolDefinition tool) {
    return FunctionToolCallback.<Map<String, Object>, Object>builder(
            tool.name(),
            args -> {
              Map<String, Object> safeArgs = args == null ? Map.of() : args;
              return tool.handler().apply(safeArgs);
            })
        .description(tool.description())
        .inputType(MAP_TYPE)
        .inputSchema(asJsonSchema(tool.parameters()))
        .build();
  }

  private String asJsonSchema(Map<String, Object> schema) {
    try {
      return objectMapper.writeValueAsString(schema);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize tool schema", e);
    }
  }
}
