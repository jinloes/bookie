package com.bookie.datalifecycle.migration;

public class MigrationIntegrityException extends IllegalStateException {

  public MigrationIntegrityException(String message) {
    super(message);
  }

  public MigrationIntegrityException(String message, Throwable cause) {
    super(message, cause);
  }
}
