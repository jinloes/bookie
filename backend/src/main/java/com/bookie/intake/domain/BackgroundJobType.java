package com.bookie.intake.domain;

public enum BackgroundJobType {
  PARSE_OUTLOOK,
  PARSE_RECEIPT,
  TRANSLATE_OUTLOOK_ID,
  MOVE_OUTLOOK,
  MOVE_RECEIPT;

  public boolean isParsing() {
    return this == PARSE_OUTLOOK || this == PARSE_RECEIPT;
  }

  public boolean isExternalSync() {
    return this == MOVE_OUTLOOK || this == MOVE_RECEIPT;
  }
}
