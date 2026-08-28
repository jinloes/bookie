package com.bookie.catalog.property.api;

import com.bookie.catalog.property.domain.Property;

public record PropertyRefResponse(Long id, String name) {

  public static PropertyRefResponse from(Property property) {
    if (property == null) {
      return null;
    }
    return new PropertyRefResponse(property.getId(), property.getName());
  }
}
