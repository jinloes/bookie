package com.bookie.catalog.classification.application;

import com.bookie.catalog.counterparty.domain.Counterparty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailKeywordPayerHistory")
public record CounterpartyKeywordHistory(
    Long id, String keyword, Counterparty payer, int occurrences, Long version) {}
