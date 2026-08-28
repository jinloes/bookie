package com.bookie.integrations.documents;

public interface DocumentTextExtractor {

  String extractText(byte[] contentBytes);

  String extractText(byte[] contentBytes, String displayName);
}
