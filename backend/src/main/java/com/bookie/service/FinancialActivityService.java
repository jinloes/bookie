package com.bookie.service;

import com.bookie.model.ActivityType;
import com.bookie.model.FinancialActivity;
import com.bookie.model.HouseholdMember;
import com.bookie.model.Property;
import com.bookie.model.TaxTreatment;
import com.bookie.model.UpsertFinancialActivityRequest;
import com.bookie.repository.FinancialActivityRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FinancialActivityService {

  public static final String NEEDS_CLASSIFICATION_KEY = "NEEDS_CLASSIFICATION";

  private final FinancialActivityRepository financialActivityRepository;
  private final HouseholdMemberService householdMemberService;
  private final PropertyService propertyService;

  public List<FinancialActivity> findAll() {
    return financialActivityRepository.findAllByOrderByNameAsc();
  }

  public FinancialActivity findById(Long id) {
    return financialActivityRepository
        .findById(id)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Financial activity not found: " + id));
  }

  public FinancialActivity findActiveById(Long id) {
    FinancialActivity activity = findById(id);
    if (!activity.isActive() || !activity.getOwner().isActive()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Financial activity is inactive: " + id);
    }
    return activity;
  }

  public FinancialActivity getNeedsClassification() {
    return financialActivityRepository
        .findBySystemKey(NEEDS_CLASSIFICATION_KEY)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required Needs classification financial activity is missing"));
  }

  public FinancialActivity resolveForTransaction(Long activityId, Long legacyPropertyId) {
    if (activityId != null) {
      FinancialActivity activity = findActiveById(activityId);
      validateLegacyProperty(activity, legacyPropertyId);
      return activity;
    }
    if (legacyPropertyId != null) {
      return financialActivityRepository
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

  public FinancialActivity resolveForProperty(Property property) {
    if (property == null) {
      return getNeedsClassification();
    }
    return financialActivityRepository
        .findByPropertyId(property.getId())
        .filter(FinancialActivity::isActive)
        .filter(activity -> activity.getOwner().isActive())
        .orElseGet(this::getNeedsClassification);
  }

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
  public FinancialActivity create(UpsertFinancialActivityRequest request) {
    HouseholdMember owner = householdMemberService.findById(request.ownerId());
    validateOwner(owner);
    Property property =
        request.propertyId() == null ? null : propertyService.findById(request.propertyId());
    validateShape(request.activityType(), request.taxTreatment(), property);
    ensureUniqueName(request.name(), null);
    ensurePropertyAvailable(property, null);
    return financialActivityRepository.save(
        FinancialActivity.builder()
            .name(request.name().trim())
            .activityType(request.activityType())
            .taxTreatment(request.taxTreatment())
            .owner(owner)
            .property(property)
            .active(request.active() == null || request.active())
            .build());
  }

  @Transactional
  public FinancialActivity update(Long id, UpsertFinancialActivityRequest request) {
    FinancialActivity existing = findById(id);
    if (existing.getSystemKey() != null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "System financial activities cannot be edited");
    }
    HouseholdMember owner = householdMemberService.findById(request.ownerId());
    validateOwner(owner);
    Property property =
        request.propertyId() == null ? null : propertyService.findById(request.propertyId());
    validateShape(request.activityType(), request.taxTreatment(), property);
    if (existing.getActivityType() == ActivityType.RENTAL
        && (request.activityType() != ActivityType.RENTAL
            || !Objects.equals(existing.getProperty().getId(), property.getId()))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A property's rental activity cannot be reassigned");
    }
    ensureUniqueName(request.name(), id);
    ensurePropertyAvailable(property, id);
    existing.setName(request.name().trim());
    existing.setActivityType(request.activityType());
    existing.setTaxTreatment(request.taxTreatment());
    existing.setOwner(owner);
    existing.setProperty(property);
    if (request.active() != null) {
      existing.setActive(request.active());
    }
    return financialActivityRepository.save(existing);
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
    financialActivityRepository
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
    financialActivityRepository
        .findByPropertyId(property.getId())
        .filter(existing -> !existing.getId().equals(currentId))
        .ifPresent(
            existing -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT, "That property already has a rental activity");
            });
  }
}
