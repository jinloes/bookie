package com.bookie.intake.application;

import com.bookie.intake.application.BackgroundJobStore.JobCandidate;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.InboxStateMachine;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.model.ExpenseSource;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BackgroundJobService {

  private static final int CLAIM_SCAN_LIMIT = 20;
  private static final int MAX_ERROR_LENGTH = 2000;

  private final BackgroundJobStore jobStore;
  private final InboxItemStore inboxItemStore;
  private final RetrySchedule retrySchedule;
  private final LegacyPendingJobStateWriter legacyPendingJobStateWriter;
  private final Clock clock;

  @Transactional
  public Optional<BackgroundJob> claimNext(
      String leaseOwner, Duration leaseDuration, Set<BackgroundJobType> allowedTypes) {
    if (allowedTypes.isEmpty()) {
      return Optional.empty();
    }
    LocalDateTime now = now();
    for (JobCandidate candidate : jobStore.findClaimable(now, CLAIM_SCAN_LIMIT, allowedTypes)) {
      Optional<BackgroundJob> claimed =
          claim(candidate.id(), candidate.version(), leaseOwner, leaseDuration, now);
      if (claimed.isPresent()) {
        return claimed;
      }
    }
    return Optional.empty();
  }

  @Transactional
  public Optional<BackgroundJob> claim(
      Long jobId, String leaseOwner, Duration leaseDuration, Set<BackgroundJobType> allowedTypes) {
    LocalDateTime now = now();
    return jobStore
        .findById(jobId)
        .filter(job -> allowedTypes.contains(job.getType()))
        .flatMap(job -> claim(job.getId(), job.getVersion(), leaseOwner, leaseDuration, now));
  }

  @Transactional
  public void complete(Long jobId, String leaseOwner, JobExecutionResult result) {
    BackgroundJob job = requireLeased(jobId, leaseOwner);
    job.setState(BackgroundJobState.COMPLETED);
    job.setLeaseOwner(null);
    job.setLeaseExpiresAt(null);
    job.setLastError(null);
    job.setTerminalReason(null);

    InboxItem item = job.getInboxItem();
    if (StringUtils.isNotBlank(result.immutableSourceId())) {
      item.setImmutableSourceId(result.immutableSourceId());
    }
    if (job.getType().isExternalSync()) {
      item.setExternalSyncState(ExternalSyncState.SUCCEEDED);
      item.setErrorMessage(null);
    }
    inboxItemStore.save(item);
    jobStore.save(job);
  }

  @Transactional
  public void fail(Long jobId, String leaseOwner, Throwable failure) {
    BackgroundJob job = requireLeased(jobId, leaseOwner);
    FailureDisposition disposition = classify(failure);
    String message = errorMessage(failure);
    boolean retry =
        disposition == FailureDisposition.RETRYABLE && job.getAttempts() < job.getMaxAttempts();

    job.setLeaseOwner(null);
    job.setLeaseExpiresAt(null);
    job.setLastError(message);
    InboxItem item = job.getInboxItem();
    if (job.getType().isParsing() || job.getType().isExternalSync()) {
      item.setErrorMessage(message);
    }

    if (retry) {
      job.setState(BackgroundJobState.AVAILABLE);
      job.setAvailableAt(retrySchedule.nextAttempt(job.getId(), job.getAttempts(), now()));
      job.setTerminalReason(null);
      markRetryPending(job, item);
      if (job.getType().isParsing()) {
        legacyPendingJobStateWriter.parsingRequeued(job);
      }
    } else {
      job.setState(BackgroundJobState.TERMINAL);
      job.setTerminalReason(
          disposition == FailureDisposition.MANUAL_REVIEW
              ? "MANUAL_REVIEW"
              : disposition == FailureDisposition.RETRYABLE ? "MAX_ATTEMPTS" : "TERMINAL");
      markTerminal(job, item, disposition);
      if (job.getType().isParsing()) {
        legacyPendingJobStateWriter.parsingFailed(job, message);
      }
    }
    inboxItemStore.save(item);
    jobStore.save(job);
  }

  @Transactional
  public BackgroundJob retry(Long jobId) {
    BackgroundJob job =
        jobStore
            .findById(jobId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Background job not found: " + jobId));
    if (job.getState() != BackgroundJobState.TERMINAL) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only terminal background jobs can be retried");
    }
    if (job.getInboxItem().getState() == InboxState.DISMISSED
        || "DISMISSED".equals(job.getTerminalReason())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Jobs for dismissed inbox items cannot be retried");
    }
    job.setState(BackgroundJobState.AVAILABLE);
    job.setAttempts(0);
    job.setAvailableAt(now());
    job.setLeaseOwner(null);
    job.setLeaseExpiresAt(null);
    job.setLastError(null);
    job.setTerminalReason(null);
    if (job.getType().isParsing() || job.getType().isExternalSync()) {
      job.getInboxItem().setErrorMessage(null);
    }
    markRetryPending(job, job.getInboxItem());
    if (job.getType().isParsing()) {
      legacyPendingJobStateWriter.parsingRequeued(job);
    }
    inboxItemStore.save(job.getInboxItem());
    return jobStore.save(job);
  }

  @Transactional
  public int recoverExpiredLeases() {
    LocalDateTime now = now();
    List<BackgroundJob> expired = jobStore.findExpiredLeases(now);
    for (BackgroundJob job : expired) {
      job.setLeaseOwner(null);
      job.setLeaseExpiresAt(null);
      InboxItem item = job.getInboxItem();
      if (job.getType().isParsing()
          && (item.getState() == InboxState.READY || item.getState() == InboxState.SAVED)) {
        job.setState(BackgroundJobState.COMPLETED);
        job.setLastError(null);
        job.setTerminalReason(null);
      } else if (job.getAttempts() >= job.getMaxAttempts()) {
        String message = "Worker lease expired on the final attempt";
        job.setState(BackgroundJobState.TERMINAL);
        job.setLastError(message);
        job.setTerminalReason("MAX_ATTEMPTS");
        if (job.getType().isParsing() || job.getType().isExternalSync()) {
          item.setErrorMessage(message);
        }
        markTerminal(job, item, FailureDisposition.RETRYABLE);
        if (job.getType().isParsing()) {
          legacyPendingJobStateWriter.parsingFailed(job, message);
        }
      } else {
        job.setState(BackgroundJobState.AVAILABLE);
        job.setAvailableAt(now);
        job.setTerminalReason(null);
        markRetryPending(job, item);
        if (job.getType().isParsing()) {
          legacyPendingJobStateWriter.parsingRequeued(job);
        }
      }
      inboxItemStore.save(item);
      jobStore.save(job);
    }
    return expired.size();
  }

  @Transactional(readOnly = true)
  public Optional<BackgroundJob> findLatest(LegacyPendingKey key, BackgroundJobType type) {
    return jobStore.findLatest(key, type);
  }

  @Transactional(readOnly = true)
  public List<BackgroundJob> findByInboxItemId(Long inboxItemId) {
    return jobStore.findByInboxItemId(inboxItemId);
  }

  @Transactional(readOnly = true)
  public Optional<BackgroundJob> findAvailableForSource(
      ExpenseSource origin, String legacySourceId, BackgroundJobType type) {
    return inboxItemStore
        .findBySourceIdentity(origin, legacySourceId)
        .flatMap(
            item ->
                jobStore.findByInboxItemId(item.getId()).stream()
                    .filter(job -> job.getType() == type)
                    .filter(job -> job.getState() == BackgroundJobState.AVAILABLE)
                    .findFirst());
  }

  private Optional<BackgroundJob> claim(
      Long jobId,
      Long expectedVersion,
      String leaseOwner,
      Duration leaseDuration,
      LocalDateTime now) {
    if (!jobStore.claim(jobId, expectedVersion, leaseOwner, now.plus(leaseDuration), now)) {
      return Optional.empty();
    }
    BackgroundJob claimed =
        jobStore
            .findById(jobId)
            .orElseThrow(() -> new IllegalStateException("Claimed job disappeared: " + jobId));
    markProcessing(claimed);
    return Optional.of(claimed);
  }

  private void markProcessing(BackgroundJob job) {
    InboxItem item = job.getInboxItem();
    if (job.getType().isParsing()) {
      InboxStateMachine.transition(item, InboxState.PROCESSING);
      item.setRawStatus("PROCESSING");
      item.setErrorMessage(null);
      legacyPendingJobStateWriter.parsingStarted(job);
    } else if (job.getType().isExternalSync()) {
      item.setExternalSyncState(ExternalSyncState.PROCESSING);
      item.setErrorMessage(null);
    }
    inboxItemStore.save(item);
  }

  private void markRetryPending(BackgroundJob job, InboxItem item) {
    if (job.getType().isParsing()) {
      InboxStateMachine.transition(item, InboxState.QUEUED);
      item.setRawStatus("PROCESSING");
      item.setErrorMessage(null);
    } else if (job.getType().isExternalSync()) {
      item.setExternalSyncState(ExternalSyncState.PENDING);
    }
  }

  private void markTerminal(BackgroundJob job, InboxItem item, FailureDisposition disposition) {
    if (job.getType().isParsing()) {
      InboxStateMachine.transition(item, InboxState.FAILED);
      item.setRawStatus("FAILED");
    } else if (job.getType().isExternalSync()) {
      item.setExternalSyncState(
          disposition == FailureDisposition.MANUAL_REVIEW
              ? ExternalSyncState.MANUAL_REVIEW
              : ExternalSyncState.FAILED);
    }
  }

  private BackgroundJob requireLeased(Long jobId, String leaseOwner) {
    BackgroundJob job =
        jobStore
            .findById(jobId)
            .orElseThrow(() -> new IllegalStateException("Background job not found: " + jobId));
    if (job.getState() != BackgroundJobState.LEASED
        || !Objects.equals(job.getLeaseOwner(), leaseOwner)) {
      throw new IllegalStateException("Background job lease is no longer owned by this worker");
    }
    return job;
  }

  private FailureDisposition classify(Throwable failure) {
    if (failure instanceof JobExecutionException jobFailure) {
      return switch (jobFailure.getKind()) {
        case RETRYABLE -> FailureDisposition.RETRYABLE;
        case MANUAL_REVIEW -> FailureDisposition.MANUAL_REVIEW;
        case TERMINAL -> FailureDisposition.TERMINAL;
      };
    }
    return FailureDisposition.TERMINAL;
  }

  private String errorMessage(Throwable failure) {
    String message = StringUtils.defaultIfBlank(failure.getMessage(), failure.getClass().getName());
    return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  private enum FailureDisposition {
    RETRYABLE,
    MANUAL_REVIEW,
    TERMINAL
  }
}
