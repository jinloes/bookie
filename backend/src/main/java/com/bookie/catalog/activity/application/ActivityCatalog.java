package com.bookie.catalog.activity.application;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.property.domain.Property;
import java.util.List;
import java.util.Optional;

public interface ActivityCatalog {

  String NEEDS_CLASSIFICATION_KEY = "NEEDS_CLASSIFICATION";

  List<FinancialActivity> findAll();

  FinancialActivity findById(Long id);

  FinancialActivity findActiveById(Long id);

  FinancialActivity getNeedsClassification();

  FinancialActivity resolveForTransaction(Long activityId, Long legacyPropertyId);

  FinancialActivity resolveForProperty(Property property);

  FinancialActivity resolveForSuggestedProperty(String propertyName);

  FinancialActivity create(UpsertFinancialActivityCommand command);

  FinancialActivity update(Long id, UpsertFinancialActivityCommand command);

  FinancialActivity createRentalActivity(Property property);

  Optional<FinancialActivity> findByPropertyId(Long propertyId);

  void delete(FinancialActivity activity);
}
