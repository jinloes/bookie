package com.bookie.catalog.financialcategory.infrastructure;

import com.bookie.catalog.financialcategory.application.FinancialCategoryStore;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaFinancialCategoryStore implements FinancialCategoryStore {

  private final FinancialCategoryRepository repository;

  @Override
  public List<FinancialCategory> findAllActive() {
    return repository.findAllByActiveTrueOrderByDirectionAscLabelAsc();
  }

  @Override
  public Optional<FinancialCategory> findById(Long id) {
    return repository.findById(id);
  }

  @Override
  public Optional<FinancialCategory> findByKey(String key) {
    return repository.findByKey(key);
  }
}
