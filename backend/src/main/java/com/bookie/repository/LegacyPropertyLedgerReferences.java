package com.bookie.repository;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.property.application.PropertyLedgerReferences;
import com.bookie.ledger.application.LedgerLifecycle;
import com.bookie.model.FinancialCategory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyPropertyLedgerReferences implements PropertyLedgerReferences {

  private final ExpenseRepository expenseRepository;
  private final IncomeRepository incomeRepository;
  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryRepository financialCategoryRepository;
  private final LedgerLifecycle ledgerLifecycle;

  @Override
  public void reassignActivity(Long activityId) {
    FinancialActivity replacement = activityCatalog.getNeedsClassification();
    FinancialCategory expenseCategory = requiredCategory("OTHER_EXPENSE");
    FinancialCategory incomeCategory = requiredCategory("OTHER_INCOME");
    ledgerLifecycle.reassignActivity(
        activityId, replacement.getId(), incomeCategory.getId(), expenseCategory.getId());
    expenseRepository.reassignClassification(activityId, replacement, expenseCategory);
    incomeRepository.reassignClassification(activityId, replacement, incomeCategory);
  }

  @Override
  public void detachProperty(Long propertyId) {
    expenseRepository.clearPropertyById(propertyId);
    incomeRepository.clearPropertyById(propertyId);
  }

  @Override
  public void requireNoReferences(Long propertyId, Optional<Long> retiredActivityId) {
    long propertyReferences =
        expenseRepository.countByPropertyId(propertyId)
            + incomeRepository.countByPropertyId(propertyId);
    long activityReferences =
        retiredActivityId
            .map(
                activityId ->
                    expenseRepository.countByActivityId(activityId)
                        + incomeRepository.countByActivityId(activityId))
            .orElse(0L);
    long unifiedActivityReferences =
        retiredActivityId.map(ledgerLifecycle::countActivityReferences).orElse(0L);
    if (propertyReferences != 0 || activityReferences != 0 || unifiedActivityReferences != 0) {
      throw new IllegalStateException(
          "Ledger records still reference property or rental activity: " + propertyId);
    }
  }

  private FinancialCategory requiredCategory(String key) {
    return financialCategoryRepository
        .findByKey(key)
        .orElseThrow(
            () -> new IllegalStateException("Required financial category is missing: " + key));
  }
}
