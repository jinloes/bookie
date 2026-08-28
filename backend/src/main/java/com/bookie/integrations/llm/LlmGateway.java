package com.bookie.integrations.llm;

public interface LlmGateway {
  String completeText(LlmTextRequest request);

  String completeVision(LlmVisionRequest request);
}
