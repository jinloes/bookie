package com.bookie.intake.sources;

public record ReceiptDto(
    String id,
    String name,
    int year,
    String webUrl,
    String uploadedAt,
    Long expenseId,
    Long incomeId,
    boolean pending) {}
