package com.bookie.intake.domain;

public enum ExternalSyncState {
  NOT_REQUIRED,
  PENDING,
  PROCESSING,
  SUCCEEDED,
  FAILED,
  MANUAL_REVIEW
}
