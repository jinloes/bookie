package com.bookie.repository;

import com.bookie.model.FinancialCategory;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
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
