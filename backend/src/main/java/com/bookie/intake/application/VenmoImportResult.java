package com.bookie.intake.application;

import lombok.Builder;

@Builder
public record VenmoImportResult(
    int totalRows,
    int importedRows,
    int skippedSenderRows,
    int skippedOutgoingRows,
    int skippedDuplicateRows,
    int skippedInvalidRows,
    String senderFilter,
    String propertyName,
    String activityName) {}
