package com.bookie.intake.application;

import lombok.Builder;

@Builder
public record BankImportResult(
    int totalRows,
    int importedRows,
    int skippedDebitRows,
    int skippedDuplicateRows,
    int skippedInvalidRows,
    String activityName) {}
