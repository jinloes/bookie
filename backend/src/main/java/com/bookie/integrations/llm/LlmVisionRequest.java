package com.bookie.integrations.llm;

import lombok.Builder;

@Builder
public record LlmVisionRequest(
    String model,
    String systemPrompt,
    String userPrompt,
    String mimeType,
    String displayName,
    byte[] binaryData) {}
