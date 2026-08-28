package com.bookie.catalog.property.application;

import com.bookie.catalog.property.domain.Property;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PropertyService implements PropertyCatalog {

  private final PropertyStore propertyStore;
  private final PropertyActivityLifecycle activityLifecycle;
  private final PropertyLedgerReferences ledgerReferences;
  private final PropertyIntakeReferences intakeReferences;
  private final PropertyClassificationReferences classificationReferences;

  @Override
  public List<Property> findAll() {
    return propertyStore.findAll();
  }

  @Override
  public Property findById(Long id) {
    return propertyStore
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found: " + id));
  }

  @Override
  public Optional<Property> findByName(String name) {
    return propertyStore.findByNameIgnoreCase(name);
  }

  @Override
  public List<Property> findByAccounts(List<String> accounts) {
    return propertyStore.findByAccountIn(accounts);
  }

  @Transactional
  @Override
  public Property create(CreatePropertyCommand command) {
    Property property =
        Property.builder()
            .name(command.name())
            .address(command.address())
            .type(command.type())
            .notes(command.notes())
            .accounts(
                command.accounts() != null ? new HashSet<>(command.accounts()) : new HashSet<>())
            .build();
    Property saved = propertyStore.save(property);
    activityLifecycle.createRentalActivity(saved);
    return saved;
  }

  @Override
  public Property update(Long id, UpdatePropertyCommand command) {
    Property existing = findById(id);
    existing.setName(command.name());
    existing.setAddress(command.address());
    existing.setType(command.type());
    existing.setNotes(command.notes());
    existing.setAccounts(
        command.accounts() != null ? new HashSet<>(command.accounts()) : new HashSet<>());
    return propertyStore.save(existing);
  }

  @Transactional
  @Override
  public void delete(Long id) {
    findById(id);
    Optional<Long> rentalActivityId = activityLifecycle.findRentalActivityId(id);
    rentalActivityId.ifPresent(
        activityId -> {
          ledgerReferences.reassignActivity(activityId);
          intakeReferences.reassignActivity(activityId);
          activityLifecycle.deleteRentalActivity(activityId);
        });
    ledgerReferences.detachProperty(id);
    intakeReferences.detachProperty(id);
    classificationReferences.removePropertyReferences(id);
    ledgerReferences.requireNoReferences(id, rentalActivityId);
    intakeReferences.requireNoReferences(id, rentalActivityId);
    classificationReferences.requireNoPropertyReferences(id);
    propertyStore.deleteById(id);
  }
}
