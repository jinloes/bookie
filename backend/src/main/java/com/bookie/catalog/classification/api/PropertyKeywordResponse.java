package com.bookie.catalog.classification.api;

import com.bookie.catalog.classification.application.PropertyKeywordHistory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailKeywordPropertyHistory")
public record PropertyKeywordResponse(
    Long id, String keyword, PropertyKeywordRefResponse property, int occurrences, Long version) {

  public record PropertyKeywordRefResponse(Long id, String name) {}

  public static PropertyKeywordResponse from(PropertyKeywordHistory history) {
    PropertyKeywordRefResponse property =
        history.propertyId() == null && history.propertyName() == null
            ? null
            : new PropertyKeywordRefResponse(history.propertyId(), history.propertyName());
    return new PropertyKeywordResponse(
        history.id(), history.keyword(), property, history.occurrences(), history.version());
  }
}
