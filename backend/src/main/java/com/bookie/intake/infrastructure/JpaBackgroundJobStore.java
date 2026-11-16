package com.bookie.intake.infrastructure;

import com.bookie.intake.application.BackgroundJobStore;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
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
  public Optional<BackgroundJob> findLatest(Long inboxItemId, BackgroundJobType type) {
    return repository.findFirstByInboxItemIdAndTypeOrderByIdDesc(inboxItemId, type);
  }

  @Override
  public List<BackgroundJob> findByInboxItemId(Long inboxItemId) {
    return repository.findAllByInboxItemIdOrderByCreatedAtAsc(inboxItemId);
  }

  @Override
  public List<BackgroundJob> findClaimableDue(
      LocalDateTime now, int limit, Set<BackgroundJobType> allowedTypes) {
    if (allowedTypes.isEmpty()) {
      return List.of();
    }
    List<BackgroundJob> jobs =
        repository.findClaimableDue(now, allowedTypes, PageRequest.of(0, limit));
    jobs.forEach(JpaBackgroundJobStore::initializeAssociations);
    return jobs;
  }

  @Override
  public Optional<BackgroundJob> findForUpdate(Long id) {
    Optional<BackgroundJob> job = repository.findForUpdate(id);
    job.ifPresent(JpaBackgroundJobStore::initializeAssociations);
    return job;
  }

  @Override
  public boolean existsDueOrActiveWork(LocalDateTime now, Set<BackgroundJobType> allowedTypes) {
    if (allowedTypes.isEmpty()) {
      return false;
    }
    return repository.existsDueOrActiveWork(now, allowedTypes);
  }

  @Override
  public Optional<LocalDateTime> findEarliestFutureAvailableAt(
      LocalDateTime now, Set<BackgroundJobType> allowedTypes) {
    if (allowedTypes.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(repository.findEarliestFutureAvailableAt(now, allowedTypes));
  }

  @Override
  public List<BackgroundJob> findByState(BackgroundJobState state) {
    List<BackgroundJob> jobs = repository.findAllByStateOrderByIdAsc(state);
    jobs.forEach(JpaBackgroundJobStore::initializeAssociations);
    return jobs;
  }

  private static void initializeAssociations(BackgroundJob job) {
    job.findInboxItem()
        .ifPresent(item -> {
          Hibernate.initialize(item);
          Hibernate.initialize(item.getArtifacts());
        });
  }

  @Override
  public int terminalizeActiveForInbox(Long inboxItemId, String reason, LocalDateTime now) {
    return repository.terminalizeActiveForInbox(
        inboxItemId, reason, now, BackgroundJobState.COMPLETED, BackgroundJobState.TERMINAL);
  }
}
