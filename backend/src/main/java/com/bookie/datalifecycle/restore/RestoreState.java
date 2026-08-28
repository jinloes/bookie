package com.bookie.datalifecycle.restore;

public enum RestoreState {
  IDLE,
  VALIDATED,
  LIVE_RETAINED,
  SHADOW_ACTIVATED,
  POST_START_VALIDATED,
  ROLLBACK_REQUIRED,
  ROLLED_BACK,
  FAILED;

  public boolean isTerminal() {
    return this == IDLE || this == POST_START_VALIDATED || this == ROLLED_BACK || this == FAILED;
  }
}
