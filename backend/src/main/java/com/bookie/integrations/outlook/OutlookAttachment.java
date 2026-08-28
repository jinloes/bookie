package com.bookie.integrations.outlook;

public record OutlookAttachment(
    String name, String contentType, boolean inline, byte[] contentBytes) {

  public OutlookAttachment {
    contentBytes = contentBytes == null ? null : contentBytes.clone();
  }

  @Override
  public byte[] contentBytes() {
    return contentBytes == null ? null : contentBytes.clone();
  }
}
