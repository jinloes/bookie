package com.bookie.catalog.activity.infrastructure;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.property.application.PropertyActivityLifecycle;
import com.bookie.catalog.property.domain.Property;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PropertyActivityLifecycleAdapter implements PropertyActivityLifecycle {

  private final ActivityCatalog activityCatalog;

  @Override
  public void createRentalActivity(Property property) {
    activityCatalog.createRentalActivity(property);
  }

  @Override
  public Optional<Long> findRentalActivityId(Long propertyId) {
    return activityCatalog.findByPropertyId(propertyId).map(activity -> activity.getId());
  }

  @Override
  public void deleteRentalActivity(Long activityId) {
    activityCatalog.delete(activityCatalog.findById(activityId));
  }
}
