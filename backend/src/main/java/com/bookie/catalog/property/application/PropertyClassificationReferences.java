package com.bookie.catalog.property.application;

public interface PropertyClassificationReferences {

  void removePropertyReferences(Long propertyId);

  void requireNoPropertyReferences(Long propertyId);
}
