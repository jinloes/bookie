package com.bookie.catalog.financialcategory.application;

import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import java.util.List;
import java.util.Optional;

/** Reads the profile-specific financial category rows the category service resolves against. */
public interface FinancialCategoryStore {

  /** Active categories ordered by direction, then label. */
  List<FinancialCategory> findAllActive();

  Optional<FinancialCategory> findById(Long id);

  Optional<FinancialCategory> findByKey(String key);
}
