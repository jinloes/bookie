package com.bookie.intake.infrastructure;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.property.application.PropertyDeleting;
import com.bookie.intake.application.InboxItemStore;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboxPropertyReferences {

  private final InboxItemStore inboxItemStore;
  private final ActivityCatalog activityCatalog;
  private final CategoryCatalog categoryCatalog;

  @EventListener
  public void onPropertyDeleting(PropertyDeleting event) {
    event.rentalActivityId().ifPresent(this::reassignActivity);
    detachProperty(event.propertyId());
    requireNoReferences(event.propertyId(), event.rentalActivityId());
  }

  public void reassignActivity(Long activityId) {
    inboxItemStore.reassignActivity(
        activityId,
        activityCatalog.getNeedsClassification().getId(),
        requiredCategoryId("OTHER_INCOME"),
        requiredCategoryId("OTHER_EXPENSE"));
  }

  public void detachProperty(Long propertyId) {
    inboxItemStore.detachProperty(propertyId);
  }

  public void requireNoReferences(Long propertyId, Optional<Long> retiredActivityId) {
    long propertyReferences = inboxItemStore.countPropertyReferences(propertyId);
    long activityReferences =
        retiredActivityId.map(inboxItemStore::countActivityReferences).orElse(0L);
    if (propertyReferences != 0 || activityReferences != 0) {
      throw new IllegalStateException(
          "Intake records still reference property or rental activity: " + propertyId);
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
