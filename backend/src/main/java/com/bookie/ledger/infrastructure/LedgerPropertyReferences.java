package com.bookie.ledger.infrastructure;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.property.application.PropertyDeleting;
import com.bookie.ledger.application.LedgerLifecycle;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerPropertyReferences {

  private final ActivityCatalog activityCatalog;
  private final CategoryCatalog categoryCatalog;
  private final LedgerLifecycle ledgerLifecycle;

  @EventListener
  public void onPropertyDeleting(PropertyDeleting event) {
    event.rentalActivityId().ifPresent(this::reassignActivity);
    requireNoReferences(event.propertyId(), event.rentalActivityId());
  }

  public void reassignActivity(Long activityId) {
    ledgerLifecycle.reassignActivity(
        activityId,
        activityCatalog.getNeedsClassification().getId(),
        requiredCategoryId("OTHER_INCOME"),
        requiredCategoryId("OTHER_EXPENSE"));
  }

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
        .map(NeutralCategory::getId)
        .orElseThrow(
            () -> new IllegalStateException("Required financial category is missing: " + key));
  }
}
