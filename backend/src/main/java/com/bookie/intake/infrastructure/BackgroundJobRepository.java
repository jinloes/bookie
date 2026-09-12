package com.bookie.intake.infrastructure;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingTable;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {

  @Override
  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  Optional<BackgroundJob> findById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  @Query("SELECT job FROM BackgroundJob job WHERE job.id = :id")
  Optional<BackgroundJob> findForUpdate(@Param("id") Long id);

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  Optional<BackgroundJob> findByIdempotencyKey(String idempotencyKey);

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  Optional<BackgroundJob> findFirstByLegacyPendingTableAndLegacyPendingIdAndTypeOrderByIdDesc(
      LegacyPendingTable legacyPendingTable, Long legacyPendingId, BackgroundJobType type);

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  List<BackgroundJob> findAllByInboxItemIdOrderByCreatedAtAsc(Long inboxItemId);

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<BackgroundJob> findAllByExecutionIdOrderByIdAsc(UUID executionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT job FROM BackgroundJob job
      WHERE job.executionId IS NULL
        AND job.state IN (com.bookie.intake.domain.BackgroundJobState.AVAILABLE,
                          com.bookie.intake.domain.BackgroundJobState.LEASED)
        AND job.availableAt <= :now AND job.type IN :allowedTypes
      ORDER BY job.availableAt, job.id
      """)
  List<BackgroundJob> findUnboundDue(
      @Param("now") LocalDateTime now,
      @Param("allowedTypes") Set<BackgroundJobType> allowedTypes,
      Pageable pageable);

  @Query(
      """
      SELECT DISTINCT job.executionId FROM BackgroundJob job
      WHERE job.executionId IS NOT NULL
        AND job.state IN (com.bookie.intake.domain.BackgroundJobState.AVAILABLE,
                          com.bookie.intake.domain.BackgroundJobState.LEASED)
      """)
  List<UUID> findActiveExecutionIds();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE BackgroundJob job
      SET job.state = :terminalState, job.terminalReason = :reason,
          job.leaseOwner = null, job.leaseExpiresAt = null,
          job.updatedAt = :now, job.version = job.version + 1
      WHERE job.inboxItem.id = :inboxItemId
        AND job.state NOT IN (:completedState, :terminalState)
      """)
  int terminalizeActiveForInbox(
      @Param("inboxItemId") Long inboxItemId,
      @Param("reason") String reason,
      @Param("now") LocalDateTime now,
      @Param("completedState") BackgroundJobState completedState,
      @Param("terminalState") BackgroundJobState terminalState);
}
