package com.bookie.model;

public record ReceiptDto(
    String id,
    String name,
    int year,
    String webUrl,
    String uploadedAt,
    Long expenseId,
    boolean pending) {}
