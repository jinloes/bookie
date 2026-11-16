package com.bookie.catalog.classification.application;

public record CounterpartyKeywordHistory(
    Long id,
    String keyword,
    Long counterpartyId,
    String counterpartyName,
    int occurrences,
    Long version) {}
