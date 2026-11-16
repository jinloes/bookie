package com.bookie.catalog.category.api;

import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;

public record FinancialCategoryResponse(
    Long id,
    String key,
    String label,
    TransactionDirection direction,
    String taxTreatment,
    String taxLine,
    boolean active,
    boolean system,
    Long neutralCategoryId) {

  /** Builds a category response whose neutral category ID is omitted (e.g. report totals). */
  public FinancialCategoryResponse(
      Long id,
      String key,
      String label,
      TransactionDirection direction,
      String taxTreatment,
      String taxLine,
      boolean active,
      boolean system) {
    this(id, key, label, direction, taxTreatment, taxLine, active, system, null);
  }

  public static FinancialCategoryResponse from(NeutralCategory category) {
    return from(category, null, null);
  }

  public static FinancialCategoryResponse from(
      NeutralCategory category, String taxTreatment, String taxLine) {
    if (category == null) {
      return null;
    }
    return new FinancialCategoryResponse(
        category.getId(),
        category.getKey(),
        category.getLabel(),
        category.getDirection(),
        taxTreatment,
        taxLine,
        category.isActive(),
        category.isSystem(),
        category.getId());
  }
}
