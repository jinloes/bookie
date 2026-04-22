package com.bookie.repository;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.Property;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for {@link Expense} entities. */
@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

  @Override
  @EntityGraph(attributePaths = {"property", "payer"})
  List<Expense> findAll();

  /** Returns all expenses for the given property. */
  @EntityGraph(attributePaths = {"property", "payer"})
  List<Expense> findByProperty(Property property);

  /** Returns all expenses with the given category. */
  @EntityGraph(attributePaths = {"property", "payer"})
  List<Expense> findByCategory(ExpenseCategory category);

  /** Returns the expense linked to the given external source ID, if any. */
  @EntityGraph(attributePaths = {"property", "payer"})
  Optional<Expense> findBySourceId(String sourceId);

  /** Returns all expenses whose source IDs are in the given collection. */
  List<Expense> findBySourceIdIn(Collection<String> sourceIds);

  /** Returns the expense linked to the given OneDrive receipt file ID, if any. */
  @EntityGraph(attributePaths = {"property", "payer"})
  Optional<Expense> findByReceiptOneDriveId(String receiptOneDriveId);

  /** Returns the sum of all expense amounts. */
  @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e")
  BigDecimal getTotalExpenses();
}
