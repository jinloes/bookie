package com.bookie.catalog.activity.infrastructure;

import com.bookie.catalog.activity.application.PropertyLookup;
import com.bookie.catalog.property.application.PropertyStore;
import com.bookie.catalog.property.domain.Property;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CatalogPropertyLookup implements PropertyLookup {

  private final PropertyStore propertyStore;

  @Override
  public Property findById(Long id) {
    return propertyStore
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found: " + id));
  }
}
