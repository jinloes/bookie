package com.bookie.service;

import com.bookie.model.Property;
import com.bookie.repository.PropertyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PropertyService {

  private final PropertyRepository propertyRepository;

  public List<Property> findAll() {
    return propertyRepository.findAll();
  }

  public Property findById(Long id) {
    return propertyRepository
        .findById(id)
        .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));
  }

  public Property save(Property property) {
    return propertyRepository.save(property);
  }

  public Property update(Long id, Property updated) {
    Property existing = findById(id);
    existing.setName(updated.getName());
    existing.setAddress(updated.getAddress());
    existing.setType(updated.getType());
    existing.setNotes(updated.getNotes());
    return propertyRepository.save(existing);
  }

  public void delete(Long id) {
    propertyRepository.deleteById(id);
  }
}
