package com.bookie.catalog.activity.application;

import com.bookie.catalog.property.domain.Property;

public interface PropertyLookup {

  Property findById(Long id);
}
