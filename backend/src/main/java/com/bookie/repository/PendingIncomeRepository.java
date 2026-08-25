package com.bookie.repository;

import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.PendingIncome;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PendingIncomeRepository extends JpaRepository<PendingIncome, Long> {

  @EntityGraph(
      attributePaths = {
        "property",
        "payer",
        "activity",
        "activity.owner",
        "activity.property",
        "financialCategory"
      })
  java.util.List<PendingIncome> findAll(Sort sort);

  Optional<PendingIncome> findBySourceId(String sourceId);

  boolean existsBySourceTypeAndSourceId(ExpenseSource sourceType, String sourceId);

  @Modifying
  @Query("UPDATE PendingIncome p SET p.property = null WHERE p.property.id = :propertyId")
  void clearPropertyById(@Param("propertyId") Long propertyId);

  @Modifying
  @Query(
      """
      UPDATE PendingIncome p
      SET p.activity = :replacement, p.financialCategory = :category
      WHERE p.activity.id = :activityId
      """)
  void reassignClassification(
      @Param("activityId") Long activityId,
      @Param("replacement") FinancialActivity replacement,
      @Param("category") FinancialCategory category);
}
