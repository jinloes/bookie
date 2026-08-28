package com.bookie.ledger.api;

import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.model.TransactionDirection;

public record NeutralCategoryResponse(
    Long id,
    String key,
    String label,
    TransactionDirection direction,
    boolean active,
    boolean system) {

  static NeutralCategoryResponse from(NeutralCategory category) {
    return new NeutralCategoryResponse(
        category.getId(),
        category.getKey(),
        category.getLabel(),
        category.getDirection(),
        category.isActive(),
        category.isSystem());
  }
}
