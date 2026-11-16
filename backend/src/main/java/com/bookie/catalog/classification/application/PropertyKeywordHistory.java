package com.bookie.catalog.classification.application;

public record PropertyKeywordHistory(
    Long id, String keyword, Long propertyId, String propertyName, int occurrences, Long version) {}
