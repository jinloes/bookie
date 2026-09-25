package com.bookie.intake.api;

import com.bookie.intake.application.VenmoImportResult;

public record VenmoImportResponse(
    int totalRows,
    int importedRows,
    int skippedSenderRows,
    int skippedOutgoingRows,
    int skippedDuplicateRows,
    int skippedInvalidRows,
    String senderFilter,
    String propertyName,
    String activityName) {

  static VenmoImportResponse from(VenmoImportResult result) {
    return new VenmoImportResponse(
        result.totalRows(),
        result.importedRows(),
        result.skippedSenderRows(),
        result.skippedOutgoingRows(),
        result.skippedDuplicateRows(),
        result.skippedInvalidRows(),
        result.senderFilter(),
        result.propertyName(),
        result.activityName());
  }
}
