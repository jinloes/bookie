package com.bookie.catalog.activity.application;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.household.application.HouseholdCatalog;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.reportpolicy.application.ReportingProfileAssignmentCatalog;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinancialActivityService implements ActivityCatalog {

  private final FinancialActivityStore financialActivityStore;
  private final HouseholdCatalog householdCatalog;
  private final PropertyLookup propertyLookup;
  private final ReportingProfileAssignmentCatalog reportingProfileAssignmentCatalog;

  @Override
  public List<FinancialActivity> findAll() {
    return financialActivityStore.findAllByOrderByNameAsc();
  }

  @Override
  public FinancialActivity findById(Long id) {
    return financialActivityStore
        .findById(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Financial activity not found: " + id));
  }

  @Override
  public FinancialActivity findActiveById(Long id) {
    FinancialActivity activity = findById(id);
    if (!activity.isActive() || !activity.getOwner().isActive()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Financial activity is inactive: " + id);
    }
    return activity;
  }

  @Override
  public FinancialActivity getNeedsClassification() {
    return financialActivityStore
        .findBySystemKey(NEEDS_CLASSIFICATION_KEY)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required Needs classification financial activity is missing"));
  }

  @Override
  public FinancialActivity resolveForTransaction(Long activityId, Long legacyPropertyId) {
    if (activityId != null) {
      FinancialActivity activity = findActiveById(activityId);
      validateLegacyProperty(activity, legacyPropertyId);
      return activity;
    }
    if (legacyPropertyId != null) {
      return financialActivityStore
          .findByPropertyId(legacyPropertyId)
          .filter(FinancialActivity::isActive)
          .filter(activity -> activity.getOwner().isActive())
          .orElseThrow(
              () ->
                  new ResponseStatusException(
                      HttpStatus.BAD_REQUEST,
                      "No active rental activity exists for property: " + legacyPropertyId));
    }
    return getNeedsClassification();
  }

  @Override
  public FinancialActivity resolveForProperty(Property property) {
    if (property == null) {
      return getNeedsClassification();
    }
    return financialActivityStore
        .findByPropertyId(property.getId())
        .filter(FinancialActivity::isActive)
        .filter(activity -> activity.getOwner().isActive())
        .orElseGet(this::getNeedsClassification);
  }

  @Override
  public FinancialActivity resolveForSuggestedProperty(String propertyName) {
    if (propertyName == null || propertyName.isBlank()) {
      return getNeedsClassification();
    }
    return findAll().stream()
        .filter(FinancialActivity::isActive)
        .filter(activity -> activity.getOwner().isActive())
        .filter(activity -> activity.getProperty() != null)
        .filter(
            activity ->
                propertyName.equalsIgnoreCase(activity.getProperty().getName())
                    || propertyName.equalsIgnoreCase(activity.getProperty().getAddress()))
        .findFirst()
        .orElseGet(this::getNeedsClassification);
  }

  @Transactional
  @Override
  public FinancialActivity create(UpsertFinancialActivityCommand command) {
    HouseholdMember owner = householdCatalog.findById(command.ownerId());
    validateOwner(owner);
    Property property =
        command.propertyId() == null ? null : propertyLookup.findById(command.propertyId());
    validateShape(command.activityType(), command.taxTreatment(), property);
    ensureUniqueName(command.name(), null);
    ensurePropertyAvailable(property, null);
    FinancialActivity saved =
        financialActivityStore.save(
            FinancialActivity.builder()
                .name(command.name().trim())
                .activityType(command.activityType())
                .taxTreatment(command.taxTreatment())
                .owner(owner)
                .property(property)
                .active(command.active() == null || command.active())
                .build());
    synchronizeReportingProfile(saved);
    return saved;
  }

  @Transactional
  @Override
  public FinancialActivity update(Long id, UpsertFinancialActivityCommand command) {
    FinancialActivity existing = findById(id);
    if (existing.getSystemKey() != null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "System financial activities cannot be edited");
    }
    HouseholdMember owner = householdCatalog.findById(command.ownerId());
    validateOwner(owner);
    Property property =
        command.propertyId() == null ? null : propertyLookup.findById(command.propertyId());
    validateShape(command.activityType(), command.taxTreatment(), property);
    if (existing.getActivityType() == ActivityType.RENTAL
        && (command.activityType() != ActivityType.RENTAL
            || !Objects.equals(existing.getProperty().getId(), property.getId()))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A property's rental activity cannot be reassigned");
    }
    ensureUniqueName(command.name(), id);
    ensurePropertyAvailable(property, id);
    existing.setName(command.name().trim());
    existing.setActivityType(command.activityType());
    existing.setTaxTreatment(command.taxTreatment());
    existing.setOwner(owner);
    existing.setProperty(property);
    if (command.active() != null) {
      existing.setActive(command.active());
    }
    FinancialActivity saved = financialActivityStore.save(existing);
    synchronizeReportingProfile(saved);
    return saved;
  }

  @Transactional
  @Override
  public FinancialActivity createRentalActivity(Property property) {
    HouseholdMember owner = householdCatalog.getDefaultHouseholdMember();
    String activityName =
        financialActivityStore.findAll().stream()
                .anyMatch(activity -> activity.getName().equalsIgnoreCase(property.getName()))
            ? property.getName() + " (" + property.getId() + ")"
            : property.getName();
    FinancialActivity saved =
        financialActivityStore.save(
            FinancialActivity.builder()
                .name(activityName)
                .activityType(ActivityType.RENTAL)
                .taxTreatment(TaxTreatment.SCHEDULE_E)
                .owner(owner)
                .property(property)
                .active(true)
                .build());
    synchronizeReportingProfile(saved);
    return saved;
  }

  @Override
  public Optional<FinancialActivity> findByPropertyId(Long propertyId) {
    return financialActivityStore.findByPropertyId(propertyId);
  }

  @Transactional
  @Override
  public void delete(FinancialActivity activity) {
    reportingProfileAssignmentCatalog.removeAll(activity.getId());
    financialActivityStore.delete(activity);
  }

  private void synchronizeReportingProfile(FinancialActivity activity) {
    reportingProfileAssignmentCatalog.synchronizeCurrent(
        activity.getId(), ReportingProfileKey.valueOf(activity.getTaxTreatment().name()));
  }

  private void validateShape(
      ActivityType activityType, TaxTreatment taxTreatment, Property property) {
    if (activityType == ActivityType.RENTAL && property == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Rental activities require a property");
    }
    if (activityType != ActivityType.RENTAL && property != null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Only rental activities may reference a property");
    }
    if ((activityType == ActivityType.RENTAL) != (taxTreatment == TaxTreatment.SCHEDULE_E)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Schedule E tax treatment requires a rental activity");
    }
  }

  private void validateLegacyProperty(FinancialActivity activity, Long legacyPropertyId) {
    Long activityPropertyId =
        activity.getProperty() == null ? null : activity.getProperty().getId();
    if (legacyPropertyId != null && !legacyPropertyId.equals(activityPropertyId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "The selected property does not match the selected financial activity");
    }
  }

  private void validateOwner(HouseholdMember owner) {
    if (!owner.isActive()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Financial activities require an active household member");
    }
  }

  private void ensureUniqueName(String name, Long currentId) {
    financialActivityStore
        .findByNameIgnoreCase(name.trim())
        .filter(existing -> !existing.getId().equals(currentId))
        .ifPresent(
            existing -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT, "A financial activity with that name already exists");
            });
  }

  private void ensurePropertyAvailable(Property property, Long currentId) {
    if (property == null) {
      return;
    }
    financialActivityStore
        .findByPropertyId(property.getId())
        .filter(existing -> !existing.getId().equals(currentId))
        .ifPresent(
            existing -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT, "That property already has a rental activity");
            });
  }
}
