package com.bookie.ledger.compatibility.api;

public record VenmoIncomeImportResponse(
    int totalRows,
    int importedRows,
    int skippedSenderRows,
    int skippedOutgoingRows,
    int skippedDuplicateRows,
    int skippedInvalidRows,
    String senderFilter,
    String propertyName,
    String activityName) {}
