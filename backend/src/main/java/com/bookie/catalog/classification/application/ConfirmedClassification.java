package com.bookie.catalog.classification.application;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.domain.Property;
import lombok.Builder;

/** Entity-free input used to record a confirmed legacy transaction classification. */
@Builder
public record ConfirmedClassification(
    Kind kind,
    String sourceId,
    FinancialActivity activity,
    Long financialCategoryId,
    Property property,
    Counterparty counterparty,
    String legacyExpenseCategory) {

  public enum Kind {
    EXPENSE,
    INCOME
  }
}
