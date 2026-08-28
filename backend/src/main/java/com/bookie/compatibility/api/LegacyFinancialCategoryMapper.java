package com.bookie.compatibility.api;

import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.model.FinancialCategory;

/** Converts the retained legacy category entity at the compatibility boundary. */
public final class LegacyFinancialCategoryMapper {

  private LegacyFinancialCategoryMapper() {}

  public static FinancialCategoryResponse toResponse(FinancialCategory category) {
    if (category == null) {
      return null;
    }
    return new FinancialCategoryResponse(
        category.getId(),
        category.getKey(),
        category.getLabel(),
        category.getDirection(),
        LegacyTaxTreatment.valueOf(category.getTaxTreatment().name()),
        category.getTaxLine(),
        category.isActive(),
        category.isSystem());
  }
}
