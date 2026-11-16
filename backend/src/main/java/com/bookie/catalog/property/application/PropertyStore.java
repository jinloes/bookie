package com.bookie.catalog.property.application;

import com.bookie.catalog.property.domain.Property;
import java.util.List;
import java.util.Optional;

public interface PropertyStore {

  List<Property> findAll();

  Optional<Property> findById(Long id);

  Optional<Property> findByNameIgnoreCase(String name);

  List<Property> findByAccountIn(List<String> accounts);

  <S extends Property> S save(S property);

  void deleteById(Long id);
}
