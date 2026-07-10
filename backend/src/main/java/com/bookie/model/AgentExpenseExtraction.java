package com.bookie.model;

import lombok.Builder;

/**
 * Raw fields the AI Agent model extracts from a freeform expense-tracking chat message, before
 * property/payer names are resolved to existing records. Never persisted directly — {@link
 * com.bookie.service.AgentService} turns this into a {@link
 * com.bookie.service.AgentService.ProposedExpense} that the user must explicitly confirm before
 * anything is saved.
 */
@Builder
public record AgentExpenseExtraction(
    Double amount,
    String description,
    String date,
    String category,
    String propertyName,
    String payerName,
    boolean needsMoreInfo,
    String followUpQuestion) {}
