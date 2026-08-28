package com.bookie.catalog.property.api;

import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import java.util.Set;

public record PropertyResponse(
    Long id, String name, String address, PropertyType type, String notes, Set<String> accounts) {

  public static PropertyResponse from(Property property) {
    if (property == null) {
      return null;
    }
    return new PropertyResponse(
        property.getId(),
        property.getName(),
        property.getAddress(),
        property.getType(),
        property.getNotes(),
        property.getAccounts());
  }
}
