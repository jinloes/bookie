package com.bookie.model;

import java.util.List;

public record EmailParseResult(
    Double amount,
    String description,
    String date,
    String category,
    String propertyName,
    String payerName,
    List<String> keywords) {}
