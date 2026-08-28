package com.bookie.intake.domain;

public enum InboxState {
  RECEIVED,
  QUEUED,
  PROCESSING,
  READY,
  SAVE_PENDING,
  SAVED,
  FAILED,
  DISMISSED
}
