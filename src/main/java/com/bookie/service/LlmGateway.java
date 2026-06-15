package com.bookie.service;

public interface LlmGateway {
  String completeText(LlmTextRequest request);

  String completeVision(LlmVisionRequest request);
}
