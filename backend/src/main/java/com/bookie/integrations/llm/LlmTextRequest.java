package com.bookie.integrations.llm;

import lombok.Builder;

@Builder
public record LlmTextRequest(String model, String systemPrompt, String userPrompt) {}
