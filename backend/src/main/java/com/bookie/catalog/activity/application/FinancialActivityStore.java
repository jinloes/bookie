package com.bookie.catalog.activity.application;

import com.bookie.catalog.activity.domain.FinancialActivity;
import java.util.List;
import java.util.Optional;

public interface FinancialActivityStore {

  List<FinancialActivity> findAll();

  List<FinancialActivity> findAllByOrderByNameAsc();

  Optional<FinancialActivity> findById(Long id);

  Optional<FinancialActivity> findByPropertyId(Long propertyId);

  Optional<FinancialActivity> findBySystemKey(String systemKey);

  Optional<FinancialActivity> findByNameIgnoreCase(String name);

  <S extends FinancialActivity> S save(S activity);

  void delete(FinancialActivity activity);
}
