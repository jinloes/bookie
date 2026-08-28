package com.bookie.catalog.classification.application;

import com.bookie.catalog.property.domain.Property;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailKeywordPropertyHistory")
public record PropertyKeywordHistory(
    Long id, String keyword, Property property, int occurrences, Long version) {}
