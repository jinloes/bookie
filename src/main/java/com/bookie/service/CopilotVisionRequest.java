package com.bookie.service;

import lombok.Builder;

@Builder
public record CopilotVisionRequest(
    String model,
    String systemPrompt,
    String userPrompt,
    String mimeType,
    String displayName,
    byte[] binaryData) {}
