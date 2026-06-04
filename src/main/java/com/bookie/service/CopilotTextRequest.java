package com.bookie.service;

import lombok.Builder;

@Builder
public record CopilotTextRequest(String model, String systemPrompt, String userPrompt) {}
