package com.bookie.ledger.infrastructure;

import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.model.TransactionDirection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

  @EntityGraph(
      attributePaths = {
        "activity",
        "activity.owner",
        "activity.property",
        "neutralCategory",
        "attachments",
        "importReferences"
      })
  Optional<FinancialTransaction> findByIdAndDeletedAtIsNull(Long id);

  @EntityGraph(
      attributePaths = {
        "activity",
        "activity.owner",
        "activity.property",
        "neutralCategory",
        "attachments",
        "importReferences"
      })
  List<FinancialTransaction> findAllByDeletedAtIsNull(Sort sort);

  @EntityGraph(
      attributePaths = {
        "activity",
        "activity.owner",
        "activity.property",
        "neutralCategory",
        "attachments",
        "importReferences"
      })
  List<FinancialTransaction> findAllByDirectionAndDeletedAtIsNull(
      TransactionDirection direction, Sort sort);

  @EntityGraph(attributePaths = {"activity", "neutralCategory"})
  List<FinancialTransaction> findAllByDateBetweenAndDeletedAtIsNull(
      LocalDate from, LocalDate to, Sort sort);

  @EntityGraph(
      attributePaths = {
        "activity",
        "activity.property",
        "neutralCategory",
        "attachments",
        "importReferences"
      })
  List<FinancialTransaction> findAllByActivityIdAndDeletedAtIsNull(Long activityId);

  @EntityGraph(
      attributePaths = {
        "activity",
        "activity.property",
        "neutralCategory",
        "attachments",
        "importReferences"
      })
  @Query(
      """
                    SELECT DISTINCT transaction
                    FROM FinancialTransaction transaction
                    JOIN transaction.importReferences reference
                    WHERE reference.origin = :origin
                      AND reference.externalId = :externalId
                    """)
  Optional<FinancialTransaction> findBySourceIdentity(
      @Param("origin") String origin, @Param("externalId") String externalId);

  long countByActivityIdAndDeletedAtIsNull(Long activityId);

  long countByCounterpartyIdAndDeletedAtIsNull(Long counterpartyId);
}
