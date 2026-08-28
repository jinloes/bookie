package com.bookie.intake.infrastructure;

import com.bookie.intake.application.BackgroundJobStore;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingKey;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaBackgroundJobStore implements BackgroundJobStore {

  private final BackgroundJobRepository repository;

  @Override
  public BackgroundJob save(BackgroundJob job) {
    return repository.save(job);
  }

  @Override
  public Optional<BackgroundJob> findById(Long id) {
    return repository.findById(id);
  }

  @Override
  public Optional<BackgroundJob> findByIdempotencyKey(String idempotencyKey) {
    return repository.findByIdempotencyKey(idempotencyKey);
  }

  @Override
  public Optional<BackgroundJob> findLatest(LegacyPendingKey key, BackgroundJobType type) {
    return repository.findFirstByLegacyPendingTableAndLegacyPendingIdAndTypeOrderByIdDesc(
        key.getTable(), key.getId(), type);
  }

  @Override
  public List<BackgroundJob> findByInboxItemId(Long inboxItemId) {
    return repository.findAllByInboxItemIdOrderByCreatedAtAsc(inboxItemId);
  }

  @Override
  public List<JobCandidate> findClaimable(LocalDateTime now, int limit) {
    return repository
        .findClaimable(BackgroundJobState.AVAILABLE, now, PageRequest.of(0, limit))
        .stream()
        .map(job -> new JobCandidate(job.getId(), job.getVersion()))
        .toList();
  }

  @Override
  public boolean claim(
      Long id,
      Long expectedVersion,
      String leaseOwner,
      LocalDateTime leaseExpiresAt,
      LocalDateTime now) {
    return repository.claim(
            id,
            expectedVersion,
            leaseOwner,
            leaseExpiresAt,
            now,
            BackgroundJobState.AVAILABLE,
            BackgroundJobState.LEASED)
        == 1;
  }

  @Override
  public List<BackgroundJob> findExpiredLeases(LocalDateTime now) {
    return repository.findExpiredLeases(BackgroundJobState.LEASED, now);
  }

  @Override
  public int terminalizeActiveForInbox(Long inboxItemId, String reason, LocalDateTime now) {
    return repository.terminalizeActiveForInbox(
        inboxItemId, reason, now, BackgroundJobState.COMPLETED, BackgroundJobState.TERMINAL);
  }
}
