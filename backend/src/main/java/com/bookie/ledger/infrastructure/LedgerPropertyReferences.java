package com.bookie.ledger.infrastructure;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.catalog.property.application.PropertyLedgerReferences;
import com.bookie.ledger.application.LedgerLifecycle;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerPropertyReferences implements PropertyLedgerReferences {

  private final ActivityCatalog activityCatalog;
  private final CategoryCatalog categoryCatalog;
  private final LedgerLifecycle ledgerLifecycle;

  @Override
  public void reassignActivity(Long activityId) {
    ledgerLifecycle.reassignActivity(
        activityId,
        activityCatalog.getNeedsClassification().getId(),
        requiredCategoryId("OTHER_INCOME"),
        requiredCategoryId("OTHER_EXPENSE"));
  }

  @Override
  public void detachProperty(Long propertyId) {
    // Ledger transactions reference a property only through their activity, which
    // reassignActivity has already moved.
  }

  @Override
  public void requireNoReferences(Long propertyId, Optional<Long> retiredActivityId) {
    long activityReferences =
        retiredActivityId.map(ledgerLifecycle::countActivityReferences).orElse(0L);
    if (activityReferences != 0) {
      throw new IllegalStateException(
          "Ledger records still reference property or rental activity: " + propertyId);
    }
  }

  private Long requiredCategoryId(String key) {
    return categoryCatalog
        .findByKey(key)
        .map(LegacyCategoryView::id)
        .orElseThrow(
            () -> new IllegalStateException("Required financial category is missing: " + key));
  }
}
