package com.bookie.service;

import java.util.List;
import lombok.Builder;

@Builder
public record LlmVisionRequest(
    String model,
    String systemPrompt,
    String userPrompt,
    String mimeType,
    String displayName,
    byte[] binaryData,
    List<LlmToolDefinition> tools) {}
