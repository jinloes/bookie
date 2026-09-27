package com.bookie.intake.api;

import com.bookie.intake.application.BankImportResult;

public record BankImportResponse(
    int totalRows,
    int importedRows,
    int skippedDebitRows,
    int skippedDuplicateRows,
    int skippedInvalidRows,
    String activityName) {

  static BankImportResponse from(BankImportResult result) {
    return new BankImportResponse(
        result.totalRows(),
        result.importedRows(),
        result.skippedDebitRows(),
        result.skippedDuplicateRows(),
        result.skippedInvalidRows(),
        result.activityName());
  }
}
