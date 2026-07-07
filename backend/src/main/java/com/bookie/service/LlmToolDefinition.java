package com.bookie.service;

import java.util.Map;
import java.util.function.Function;
import lombok.Builder;

@Builder
public record LlmToolDefinition(
    String name,
    String description,
    Map<String, Object> parameters,
    Function<Map<String, Object>, Object> handler) {}
