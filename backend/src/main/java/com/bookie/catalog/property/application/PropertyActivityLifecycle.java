package com.bookie.catalog.property.application;

import com.bookie.catalog.property.domain.Property;
import java.util.Optional;

public interface PropertyActivityLifecycle {

  void createRentalActivity(Property property);

  Optional<Long> findRentalActivityId(Long propertyId);

  void deleteRentalActivity(Long activityId);
}
