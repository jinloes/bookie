package com.bookie.repository;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.property.domain.Property;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Income;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeRepository extends JpaRepository<Income, Long> {

  @Override
  @EntityGraph(
      attributePaths = {
        "property",
        "property.accounts",
        "payer",
        "payer.aliases",
        "payer.accounts",
        "activity",
        "activity.owner",
        "activity.property",
        "financialCategory"
      })
  List<Income> findAll();

  @Override
  @EntityGraph(
      attributePaths = {
        "property",
        "property.accounts",
        "payer",
        "payer.aliases",
        "payer.accounts",
        "activity",
        "activity.owner",
        "activity.property",
        "financialCategory"
      })
  List<Income> findAll(Sort sort);

  @Override
  @EntityGraph(
      attributePaths = {
        "property",
        "property.accounts",
        "payer",
        "payer.aliases",
        "payer.accounts",
        "activity",
        "activity.owner",
        "activity.property",
        "financialCategory"
      })
  Optional<Income> findById(Long id);

  @EntityGraph(
      attributePaths = {
        "property",
        "property.accounts",
        "payer",
        "payer.aliases",
        "payer.accounts",
        "activity",
        "activity.owner",
        "activity.property",
        "financialCategory"
      })
  List<Income> findByProperty(Property property);

  List<Income> findBySourceIdIn(List<String> sourceIds);

  List<Income> findByOutlookMessageIdIn(Collection<String> outlookMessageIds);

  boolean existsBySourceTypeAndSourceId(ExpenseSource sourceType, String sourceId);

  @EntityGraph(
      attributePaths = {
        "property",
        "property.accounts",
        "payer",
        "payer.aliases",
        "payer.accounts"
      })
  Optional<Income> findByReceiptOneDriveId(String receiptOneDriveId);

  List<Income> findByReceiptOneDriveIdIn(Collection<String> receiptOneDriveIds);

  @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i")
  BigDecimal getTotalIncome();

  @Query(
      """
      SELECT i.activity.id AS activityId, COALESCE(SUM(i.amount), 0) AS total
      FROM Income i
      WHERE i.date BETWEEN :from AND :to
      GROUP BY i.activity.id
      """)
  List<ActivityTotalProjection> sumByActivityBetween(
      @Param("from") LocalDate from, @Param("to") LocalDate to);

  /** Detaches a deleted property from all income records without removing them. */
  @Modifying
  @Query("UPDATE Income i SET i.property = null WHERE i.property.id = :propertyId")
  void clearPropertyById(@Param("propertyId") Long propertyId);

  @Modifying
  @Query("UPDATE Income income SET income.payer = null WHERE income.payer.id = :payerId")
  void clearPayerById(@Param("payerId") Long payerId);

  long countByPropertyId(Long propertyId);

  long countByActivityId(Long activityId);

  long countByPayerId(Long payerId);

  @Modifying
  @Query(
      """
      UPDATE Income i
      SET i.activity = :replacement, i.financialCategory = :category
      WHERE i.activity.id = :activityId
      """)
  void reassignClassification(
      @Param("activityId") Long activityId,
      @Param("replacement") FinancialActivity replacement,
      @Param("category") FinancialCategory category);
}
