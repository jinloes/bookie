package com.bookie.intake.infrastructure;

import com.bookie.intake.application.BackgroundJobStore;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingKey;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
  public List<BackgroundJob> findUnboundDue(
      LocalDateTime now, int limit, Set<BackgroundJobType> allowedTypes) {
    return repository.findUnboundDue(now, allowedTypes, PageRequest.of(0, limit));
  }

  @Override
  public Optional<BackgroundJob> findForUpdate(Long id) {
    return repository.findForUpdate(id);
  }

  @Override
  public List<UUID> findActiveExecutionIds() {
    return repository.findActiveExecutionIds();
  }

  @Override
  public List<BackgroundJob> findByExecutionId(UUID executionId) {
    return repository.findAllByExecutionIdOrderByIdAsc(executionId);
  }

  @Override
  public int terminalizeActiveForInbox(Long inboxItemId, String reason, LocalDateTime now) {
    return repository.terminalizeActiveForInbox(
        inboxItemId, reason, now, BackgroundJobState.COMPLETED, BackgroundJobState.TERMINAL);
  }
}
