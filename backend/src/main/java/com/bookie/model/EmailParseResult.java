package com.bookie.model;

import java.util.List;
import lombok.Builder;

@Builder
public record EmailParseResult(
    EmailType emailType,
    Double amount,
    String description,
    String date,
    String category,
    String propertyName,
    String payerName,
    List<String> keywords,
    List<String> accountNumbers) {}
