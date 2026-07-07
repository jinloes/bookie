package com.bookie.service;

import java.util.List;
import lombok.Builder;

@Builder
public record LlmTextRequest(
    String model, String systemPrompt, String userPrompt, List<LlmToolDefinition> tools) {}
