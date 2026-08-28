package com.bookie.catalog.property.application;

import java.util.Optional;

public interface PropertyIntakeReferences {

  void reassignActivity(Long activityId);

  void detachProperty(Long propertyId);

  void requireNoReferences(Long propertyId, Optional<Long> retiredActivityId);
}
