package com.bookie.repository;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.property.application.PropertyIntakeReferences;
import com.bookie.model.FinancialCategory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyPropertyIntakeReferences implements PropertyIntakeReferences {

  private final PendingIncomeRepository pendingIncomeRepository;
  private final PendingExpenseRepository pendingExpenseRepository;
  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryRepository financialCategoryRepository;

  @Override
  public void reassignActivity(Long activityId) {
    FinancialActivity replacement = activityCatalog.getNeedsClassification();
    FinancialCategory incomeCategory = requiredCategory("OTHER_INCOME");
    FinancialCategory expenseCategory = requiredCategory("OTHER_EXPENSE");
    pendingIncomeRepository.reassignClassification(activityId, replacement, incomeCategory);
    pendingExpenseRepository.reassignIncomeClassification(activityId, replacement, incomeCategory);
    pendingExpenseRepository.reassignExpenseClassification(
        activityId, replacement, expenseCategory);
  }

  @Override
  public void detachProperty(Long propertyId) {
    pendingIncomeRepository.clearPropertyById(propertyId);
  }

  @Override
  public void requireNoReferences(Long propertyId, Optional<Long> retiredActivityId) {
    long propertyReferences = pendingIncomeRepository.countByPropertyId(propertyId);
    long activityReferences =
        retiredActivityId
            .map(
                activityId ->
                    pendingIncomeRepository.countByActivityId(activityId)
                        + pendingExpenseRepository.countByActivityId(activityId))
            .orElse(0L);
    if (propertyReferences != 0 || activityReferences != 0) {
      throw new IllegalStateException(
          "Intake records still reference property or rental activity: " + propertyId);
    }
  }

  private FinancialCategory requiredCategory(String key) {
    return financialCategoryRepository
        .findByKey(key)
        .orElseThrow(
            () -> new IllegalStateException("Required financial category is missing: " + key));
  }
}
