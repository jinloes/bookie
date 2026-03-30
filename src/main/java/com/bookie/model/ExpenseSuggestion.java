package com.bookie.model;

public record ExpenseSuggestion(
    Double amount,
    String description,
    String date,
    String category,
    String propertyName,
    String payerName,
    ExpenseSource sourceType,
    String sourceId) {}
