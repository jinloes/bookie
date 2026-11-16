package com.bookie.intake.infrastructure;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT job FROM BackgroundJob job WHERE job.id = :id")
  Optional<BackgroundJob> findForUpdate(@Param("id") Long id);

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  Optional<BackgroundJob> findByIdempotencyKey(String idempotencyKey);

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  Optional<BackgroundJob> findFirstByInboxItemIdAndTypeOrderByIdDesc(
      Long inboxItemId, BackgroundJobType type);

  @EntityGraph(attributePaths = {"inboxItem", "inboxItem.artifacts"})
  List<BackgroundJob> findAllByInboxItemIdOrderByCreatedAtAsc(Long inboxItemId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<BackgroundJob> findAllByStateOrderByIdAsc(BackgroundJobState state);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT job FROM BackgroundJob job
      WHERE job.type IN :allowedTypes
        AND ((job.state = com.bookie.intake.domain.BackgroundJobState.AVAILABLE
              AND job.availableAt <= :now)
             OR (job.state = com.bookie.intake.domain.BackgroundJobState.LEASED
                 AND (job.leaseExpiresAt IS NULL OR job.leaseExpiresAt <= :now)))
      ORDER BY job.availableAt, job.id
      """)
  List<BackgroundJob> findClaimableDue(
      @Param("now") LocalDateTime now,
      @Param("allowedTypes") Set<BackgroundJobType> allowedTypes,
      Pageable pageable);

  @Query(
      """
      SELECT COUNT(job) > 0 FROM BackgroundJob job
      WHERE job.type IN :allowedTypes
        AND (job.state = com.bookie.intake.domain.BackgroundJobState.LEASED
             OR (job.state = com.bookie.intake.domain.BackgroundJobState.AVAILABLE
                 AND job.availableAt <= :now))
      """)
  boolean existsDueOrActiveWork(
      @Param("now") LocalDateTime now, @Param("allowedTypes") Set<BackgroundJobType> allowedTypes);

  @Query(
      """
      SELECT MIN(job.availableAt) FROM BackgroundJob job
      WHERE job.type IN :allowedTypes
        AND job.state = com.bookie.intake.domain.BackgroundJobState.AVAILABLE
        AND job.availableAt > :now
      """)
  LocalDateTime findEarliestFutureAvailableAt(
      @Param("now") LocalDateTime now, @Param("allowedTypes") Set<BackgroundJobType> allowedTypes);

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
