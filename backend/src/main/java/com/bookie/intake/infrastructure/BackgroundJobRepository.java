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

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  Optional<BackgroundJob> findByIdempotencyKey(String idempotencyKey);

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  Optional<BackgroundJob> findFirstByLegacyPendingTableAndLegacyPendingIdAndTypeOrderByIdDesc(
      LegacyPendingTable legacyPendingTable, Long legacyPendingId, BackgroundJobType type);

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  List<BackgroundJob> findAllByInboxItemIdOrderByCreatedAtAsc(Long inboxItemId);

  @Query(
      """
      SELECT job
      FROM BackgroundJob job
      WHERE job.state = :availableState
        AND job.availableAt <= :now
        AND job.attempts < job.maxAttempts
        AND job.type IN :allowedTypes
      ORDER BY job.availableAt ASC, job.id ASC
      """)
  List<BackgroundJob> findClaimable(
      @Param("availableState") BackgroundJobState availableState,
      @Param("now") LocalDateTime now,
      @Param("allowedTypes") Set<BackgroundJobType> allowedTypes,
      Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE BackgroundJob job
      SET job.state = :leasedState,
          job.leaseOwner = :leaseOwner,
          job.leaseExpiresAt = :leaseExpiresAt,
          job.attempts = CASE
            WHEN job.attempts < job.maxAttempts THEN job.attempts + 1
            ELSE job.attempts
          END,
          job.updatedAt = :now,
          job.version = job.version + 1
      WHERE job.id = :id
        AND job.version = :expectedVersion
        AND job.state = :availableState
        AND job.availableAt <= :now
        AND job.attempts < job.maxAttempts
      """)
  int claim(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("leaseOwner") String leaseOwner,
      @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
      @Param("now") LocalDateTime now,
      @Param("availableState") BackgroundJobState availableState,
      @Param("leasedState") BackgroundJobState leasedState);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = "inboxItem")
  @Query(
      """
      SELECT job
      FROM BackgroundJob job
      WHERE job.state = :leasedState
        AND job.leaseExpiresAt <= :now
      ORDER BY job.leaseExpiresAt ASC, job.id ASC
      """)
  List<BackgroundJob> findExpiredLeases(
      @Param("leasedState") BackgroundJobState leasedState, @Param("now") LocalDateTime now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE BackgroundJob job
      SET job.state = :terminalState,
          job.terminalReason = :reason,
          job.leaseOwner = null,
          job.leaseExpiresAt = null,
          job.updatedAt = :now,
          job.version = job.version + 1
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
