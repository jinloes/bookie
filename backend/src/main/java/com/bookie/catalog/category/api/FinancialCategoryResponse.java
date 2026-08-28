package com.bookie.catalog.category.api;

import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.model.TransactionDirection;

public record FinancialCategoryResponse(
    Long id,
    String key,
    String label,
    TransactionDirection direction,
    LegacyTaxTreatment taxTreatment,
    String taxLine,
    boolean active,
    boolean system) {

  public static FinancialCategoryResponse from(LegacyCategoryView category) {
    if (category == null) {
      return null;
    }
    return new FinancialCategoryResponse(
        category.id(),
        category.key(),
        category.label(),
        category.direction(),
        category.taxTreatment(),
        category.taxLine(),
        category.active(),
        category.system());
  }
}
