package com.bookie.catalog.financialcategory.infrastructure;

import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialCategoryRepository extends JpaRepository<FinancialCategory, Long> {

  Optional<FinancialCategory> findByKey(String key);

  List<FinancialCategory> findAllByActiveTrueOrderByDirectionAscLabelAsc();

  List<FinancialCategory> findAllByDirectionAndTaxTreatmentAndActiveTrueOrderByLabelAsc(
      TransactionDirection direction, TaxTreatment taxTreatment);
}
