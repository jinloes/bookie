package com.bookie.model;

/**
 * Structured history hint used for payer/property/category resolution.
 *
 * @param value resolved canonical value (property name, payer name, or category key)
 * @param occurrences number of historical confirmations for this value
 * @param source origin of the hint (e.g. payer-history, keyword-history)
 */
public record HistoryHint(String value, int occurrences, String source) {}
