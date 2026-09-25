package com.bookie.intake.infrastructure;

import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InboxItemRepository extends JpaRepository<InboxItem, Long> {

  @Override
  @EntityGraph(attributePaths = "artifacts")
  Optional<InboxItem> findById(Long id);

  @EntityGraph(attributePaths = "artifacts")
  Optional<InboxItem> findByOriginAndLegacySourceId(ExpenseSource origin, String legacySourceId);

  @Override
  @EntityGraph(attributePaths = "artifacts")
  List<InboxItem> findAll(Sort sort);

  @EntityGraph(attributePaths = "artifacts")
  List<InboxItem> findByStateNotInOrderByCreatedAtDescIdDesc(Collection<InboxState> states);

  @EntityGraph(attributePaths = "artifacts")
  @Query(
      """
      SELECT DISTINCT item
      FROM InboxItem item
      JOIN item.artifacts artifact
      WHERE item.origin = com.bookie.ledger.domain.ExpenseSource.OUTLOOK_EMAIL
        AND item.state NOT IN :terminalStates
        AND artifact.type = com.bookie.intake.domain.InboxArtifactType.OUTLOOK_EMAIL
        AND artifact.externalId IN :messageIds
      ORDER BY item.id
      """)
  List<InboxItem> findActiveForOutlookMessages(
      @Param("messageIds") Collection<String> messageIds,
      @Param("terminalStates") Collection<InboxState> terminalStates);

  @Modifying
  @Query(
      """
      UPDATE InboxItem item
      SET item.legacyActivityId = :replacementActivityId,
          item.legacyCategoryId =
              CASE
                  WHEN item.proposedDirection = com.bookie.catalog.category.domain.TransactionDirection.INCOME
                  THEN :incomeCategoryId
                  ELSE :expenseCategoryId
              END
      WHERE item.legacyActivityId = :activityId
      """)
  void reassignClassifiedActivity(
      @Param("activityId") Long activityId,
      @Param("replacementActivityId") Long replacementActivityId,
      @Param("incomeCategoryId") Long incomeCategoryId,
      @Param("expenseCategoryId") Long expenseCategoryId);

  @Modifying
  @Query(
      """
      UPDATE InboxItem item
      SET item.configuredActivityId = null
      WHERE item.configuredActivityId = :activityId
      """)
  void clearConfiguredActivity(@Param("activityId") Long activityId);

  @Modifying
  @Query(
      "UPDATE InboxItem item SET item.legacyPropertyId = null"
          + " WHERE item.legacyPropertyId = :propertyId")
  void clearProperty(@Param("propertyId") Long propertyId);

  @Modifying
  @Query(
      "UPDATE InboxItem item SET item.legacyCounterpartyId = null"
          + " WHERE item.legacyCounterpartyId = :counterpartyId")
  void clearCounterparty(@Param("counterpartyId") Long counterpartyId);

  @Query(
      """
      SELECT COUNT(item)
      FROM InboxItem item
      WHERE item.legacyActivityId = :activityId
         OR item.configuredActivityId = :activityId
      """)
  long countActivityReferences(@Param("activityId") Long activityId);

  long countByLegacyPropertyId(Long legacyPropertyId);

  long countByLegacyCounterpartyId(Long legacyCounterpartyId);
}
