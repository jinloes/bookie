package com.bookie.repository;

import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingExpenseRepository extends JpaRepository<PendingExpense, Long> {

  // The list endpoint serializes pending expenses including their @ElementCollection of
  // unrecognized aliases. Fetch the collection eagerly in one query so serialization can't trip
  // a LazyInitializationException when the session is no longer available.
  @Override
  @EntityGraph(
      attributePaths = {
        "unrecognizedAliases",
        "activity",
        "activity.owner",
        "activity.property",
        "financialCategory"
      })
  List<PendingExpense> findAll(Sort sort);

  List<PendingExpense> findBySourceIdIn(Collection<String> sourceIds);

  Optional<PendingExpense> findBySourceId(String sourceId);

  List<PendingExpense> findByStatus(PendingExpenseStatus status);

  @Modifying
  @Query(
      """
      UPDATE PendingExpense p
      SET p.activity = :replacement, p.financialCategory = :category
      WHERE p.activity.id = :activityId
        AND p.emailType = com.bookie.model.EmailType.INCOME
      """)
  void reassignIncomeClassification(
      @Param("activityId") Long activityId,
      @Param("replacement") FinancialActivity replacement,
      @Param("category") FinancialCategory category);

  @Modifying
  @Query(
      """
      UPDATE PendingExpense p
      SET p.activity = :replacement, p.financialCategory = :category
      WHERE p.activity.id = :activityId
        AND (p.emailType IS NULL OR p.emailType <> com.bookie.model.EmailType.INCOME)
      """)
  void reassignExpenseClassification(
      @Param("activityId") Long activityId,
      @Param("replacement") FinancialActivity replacement,
      @Param("category") FinancialCategory category);
}
