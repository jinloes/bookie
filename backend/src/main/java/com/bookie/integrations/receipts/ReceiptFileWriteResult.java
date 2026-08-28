package com.bookie.integrations.receipts;

import java.nio.file.Path;

public record ReceiptFileWriteResult(
    Path path, Status status, String expectedSha256, String actualSha256) {

  public enum Status {
    CREATED,
    ALREADY_PRESENT,
    CONFLICT
  }
}
