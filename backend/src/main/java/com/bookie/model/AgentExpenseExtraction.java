package com.bookie.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

/**
 * Raw cashflow fields extracted from a freeform chat message. Activity, owner, category, property,
 * and tax treatment are intentionally absent and are resolved deterministically after extraction.
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentExpenseExtraction(
    @JsonAlias("emailType") TransactionDirection direction,
    Double amount,
    String description,
    String date,
    @JsonAlias("payerName") String counterpartyName,
    boolean needsMoreInfo,
    String followUpQuestion) {}
