package com.bookie.integrations.outlook;

public record OutlookMoveResult(Status status, OutlookMessageIdentity identity) {

  public enum Status {
    MOVED,
    ALREADY_AT_DESTINATION
  }
}
