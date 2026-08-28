package com.bookie.catalog.activity.infrastructure;

import com.bookie.catalog.activity.application.FinancialActivityStore;
import com.bookie.catalog.activity.domain.FinancialActivity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialActivityRepository
    extends JpaRepository<FinancialActivity, Long>, FinancialActivityStore {

  @Override
  @EntityGraph(attributePaths = {"owner", "property", "property.accounts"})
  List<FinancialActivity> findAll();

  @Override
  @EntityGraph(attributePaths = {"owner", "property", "property.accounts"})
  Optional<FinancialActivity> findById(Long id);

  @EntityGraph(attributePaths = {"owner", "property", "property.accounts"})
  List<FinancialActivity> findAllByOrderByNameAsc();

  @EntityGraph(attributePaths = {"owner", "property", "property.accounts"})
  Optional<FinancialActivity> findByPropertyId(Long propertyId);

  @EntityGraph(attributePaths = {"owner", "property", "property.accounts"})
  Optional<FinancialActivity> findBySystemKey(String systemKey);

  Optional<FinancialActivity> findByNameIgnoreCase(String name);
}
