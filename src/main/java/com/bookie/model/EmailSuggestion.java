package com.bookie.model;

import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record EmailSuggestion(
    EmailType emailType,
    Double amount,
    String description,
    String date,
    String category,
    String propertyName,
    String payerName,
    ExpenseSource sourceType,
    String sourceId,
    List<String> keywords,
    List<String> accountNumbers) {}
