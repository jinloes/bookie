package com.bookie.catalog.property.infrastructure;

import com.bookie.catalog.property.application.PropertyStore;
import com.bookie.catalog.property.domain.Property;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaPropertyStore implements PropertyStore {

  private final PropertyRepository propertyRepository;

  @Override
  public List<Property> findAll() {
    return propertyRepository.findAll();
  }

  @Override
  public Optional<Property> findById(Long id) {
    return propertyRepository.findById(id);
  }

  @Override
  public Optional<Property> findByNameIgnoreCase(String name) {
    return propertyRepository.findByNameIgnoreCase(name);
  }

  @Override
  public List<Property> findByAccountIn(List<String> accounts) {
    return propertyRepository.findByAccountIn(accounts);
  }

  @Override
  public Property save(Property property) {
    return propertyRepository.save(property);
  }

  @Override
  public void deleteById(Long id) {
    propertyRepository.deleteById(id);
  }
}
