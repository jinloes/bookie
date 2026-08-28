package com.bookie.catalog.property.application;

import com.bookie.catalog.property.domain.Property;
import java.util.List;
import java.util.Optional;

public interface PropertyCatalog {

  List<Property> findAll();

  Property findById(Long id);

  Optional<Property> findByName(String name);

  List<Property> findByAccounts(List<String> accounts);

  Property create(CreatePropertyCommand command);

  Property update(Long id, UpdatePropertyCommand command);

  void delete(Long id);
}
