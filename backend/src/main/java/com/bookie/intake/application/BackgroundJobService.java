package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.InboxStateMachine;
import com.bookie.ledger.domain.ExpenseSource;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BackgroundJobService {

  public static final int DEFAULT_MAX_ATTEMPTS = 11;
  private static final int MAX_BACKOFF_EXPONENT = 10;
  private static final ThreadLocal<Execution> CURRENT_EXECUTION = new ThreadLocal<>();

  private final BackgroundJobStore jobStore;
  private final InboxItemStore inboxItemStore;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Claims up to {@code limit} due or expired-lease jobs for execution in one short transaction,
   * recording lease ownership and start state before any remote provider call runs.
   */
  @Transactional
  public List<BackgroundJob> claimDue(
      int limit, Set<BackgroundJobType> allowedTypes, Duration leaseDuration) {
    if (allowedTypes.isEmpty() || limit <= 0) {
      return List.of();
    }
    LocalDateTime now = now();
    List<BackgroundJob> claimed = new ArrayList<>();
    for (BackgroundJob job : jobStore.findClaimableDue(now, limit, allowedTypes)) {
      if (refusedForDismissal(job)) {
        terminalDismissed(job);
        save(job);
        continue;
      }
      if (finished(job)) {
        continue;
      }
      if (acceptedParsing(job)) {
        completed(job, JobExecutionResult.completed());
        save(job);
        continue;
      }
      if (job.getAttempts() >= job.getMaxAttempts()) {
        terminal(
            job,
            "MAX_ATTEMPTS",
            StringUtils.defaultIfBlank(job.getLastError(), "Maximum retry attempts exhausted"),
            false);
        save(job);
        continue;
      }
      job.setAttempts(Math.addExact(job.getAttempts(), 1));
      job.setState(BackgroundJobState.LEASED);
      job.setLeaseOwner("intake-worker:" + UUID.randomUUID());
      job.setLeaseExpiresAt(now.plus(leaseDuration));
      markProcessing(job);
      save(job);
      claimed.add(job);
    }
    return List.copyOf(claimed);
  }

  /**
   * Whether any allowed job is currently leased or due now. Future retry backoffs do not count so
   * on-demand drains can release the database session between retries.
   */
  @Transactional(readOnly = true)
  public boolean hasDueOrBoundWork(Set<BackgroundJobType> allowedTypes) {
    return !allowedTypes.isEmpty() && jobStore.existsDueOrActiveWork(now(), allowedTypes);
  }

  /**
   * Delay until the earliest future {@code AVAILABLE} backoff retry, so {@code ON_DEMAND} mode can
   * stop polling while idle without stranding retries longer than the quiet period.
   */
  @Transactional(readOnly = true)
  public Optional<Duration> nextRetryDelay(Set<BackgroundJobType> allowedTypes) {
    if (allowedTypes.isEmpty()) {
      return Optional.empty();
    }
    LocalDateTime now = now();
    return jobStore
        .findEarliestFutureAvailableAt(now, allowedTypes)
        .map(nextAt -> Duration.between(now, nextAt))
        .map(delay -> delay.isNegative() ? Duration.ZERO : delay);
  }

  /**
   * Releases any in-flight leases back to {@code AVAILABLE} due immediately (used after an in-place
   * portable backup restore).
   */
  @Transactional
  public int releaseEngineBindings() {
    int released = 0;
    LocalDateTime now = now();
    for (BackgroundJob job : jobStore.findByState(BackgroundJobState.LEASED)) {
      if (finished(job)) {
        continue;
      }
      job.setState(BackgroundJobState.AVAILABLE);
      job.setAvailableAt(now);
      clearLease(job);
      requeued(job);
      save(job);
      released++;
    }
    return released;
  }

  @Transactional
  public boolean finish(Long jobId, String expectedLeaseOwner, JobExecutionOutcome outcome) {
    Optional<BackgroundJob> current = current(jobId, expectedLeaseOwner);
    if (current.isEmpty()) {
      return false;
    }
    BackgroundJob job = current.get();
    if (outcome.successful() || acceptedParsing(job)) {
      completed(job, outcome.successful() ? outcome.result() : JobExecutionResult.completed());
      save(job);
      return false;
    }
    Throwable failure = outcome.failure();
    boolean retryable = isRetryableFailure(failure);
    if (retryable && job.getAttempts() < job.getMaxAttempts()) {
      job.setState(BackgroundJobState.AVAILABLE);
      clearLease(job);
      job.setLastError(errorMessage(failure));
      job.setTerminalReason(null);
      job.setAvailableAt(now().plusSeconds(retryBackoffSeconds(job.getAttempts())));
      requeued(job);
      save(job);
      return true;
    }
    if (retryable) {
      terminal(job, "MAX_ATTEMPTS", errorMessage(failure), false);
      save(job);
      return false;
    }
    boolean manual =
        failure instanceof JobExecutionException executionFailure
            && executionFailure.getKind() == JobExecutionException.FailureKind.MANUAL_REVIEW;
    terminal(job, manual ? "MANUAL_REVIEW" : "TERMINAL", errorMessage(failure), manual);
    save(job);
    return false;
  }

  public static long retryBackoffSeconds(int completedAttempts) {
    int exponent = Math.clamp(completedAttempts, 1, MAX_BACKOFF_EXPONENT);
    long delay = 1L;
    for (int i = 0; i < exponent; i++) {
      delay *= 3L;
    }
    return delay;
  }

  private static boolean isRetryableFailure(Throwable failure) {
    if (failure instanceof JobExecutionException executionFailure) {
      return executionFailure.getKind() == JobExecutionException.FailureKind.RETRYABLE;
    }
    return failure instanceof DataAccessException || failure instanceof TransactionException;
  }

  @Transactional
  public BackgroundJob retry(Long jobId) {
    BackgroundJob job =
        jobStore
            .findForUpdate(jobId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Background job not found: " + jobId));
    if (job.getState() != BackgroundJobState.TERMINAL) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only terminal background jobs can be retried");
    }
    if (inboxState(job) == InboxState.DISMISSED || "DISMISSED".equals(job.getTerminalReason())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Jobs for dismissed inbox items cannot be retried");
    }
    resetGeneration(job, now());
    requeued(job);
    save(job);
    publishQueued(job);
    return job;
  }

  public static void resetGeneration(BackgroundJob job, LocalDateTime now) {
    job.setState(BackgroundJobState.AVAILABLE);
    job.setAttempts(0);
    job.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
    job.setAvailableAt(now);
    clearLease(job);
    job.setLastError(null);
    job.setTerminalReason(null);
    job.findInboxItem().ifPresent(item -> item.setErrorMessage(null));
  }

  // Parsing commits its proposal inside the dispatcher. Carry the lease token across that
  // transaction so the proposal writer can reject a stale write before it commits.
  static void enterExecution(BackgroundJob job) {
    CURRENT_EXECUTION.set(new Execution(job.getId(), job.getLeaseOwner()));
  }

  static void leaveExecution() {
    CURRENT_EXECUTION.remove();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireCurrentParse(Long inboxItemId) {
    Execution execution = CURRENT_EXECUTION.get();
    if (execution == null) {
      return;
    }
    BackgroundJob job =
        current(execution.jobId(), execution.leaseOwner())
            .orElseThrow(() -> new IllegalStateException("Stale intake parsing generation"));
    if (acceptedParsing(job)
        || !Objects.equals(job.findInboxItem().map(InboxItem::getId).orElse(null), inboxItemId)) {
      throw new IllegalStateException("Intake parsing result no longer applies");
    }
  }

  @Transactional(readOnly = true)
  public Optional<BackgroundJob> findLatest(Long inboxItemId, BackgroundJobType type) {
    return jobStore.findLatest(inboxItemId, type);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public BackgroundJob schedule(InboxItem item, BackgroundJobType type, Integer targetYear) {
    return ensureJob(item, type, targetYear, true);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public BackgroundJob scheduleIfAbsent(InboxItem item, BackgroundJobType type) {
    return ensureJob(item, type, null, false);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public BackgroundJob scheduleReceiptMove(
      Long transactionId, String receiptExternalId, int targetYear) {
    String idempotencyKey = "move_receipt:transaction:" + transactionId;
    BackgroundJob job =
        jobStore
            .findByIdempotencyKey(idempotencyKey)
            .orElseGet(() ->
                jobStore.save(
                    BackgroundJob.builder()
                        .type(BackgroundJobType.MOVE_RECEIPT)
                        .sourceExternalId(receiptExternalId)
                        .targetYear(targetYear)
                        .idempotencyKey(idempotencyKey)
                        .state(BackgroundJobState.AVAILABLE)
                        .attempts(0)
                        .maxAttempts(DEFAULT_MAX_ATTEMPTS)
                        .availableAt(now())
                        .build()));
    publishQueued(job);
    return job;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public int terminalizeActive(Long inboxItemId, String reason) {
    return jobStore.terminalizeActiveForInbox(inboxItemId, reason, now());
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
        .flatMap(item ->
            jobStore.findByInboxItemId(item.getId()).stream()
                .filter(job -> job.getType() == type)
                .filter(job -> job.getState() == BackgroundJobState.AVAILABLE)
                .findFirst());
  }

  private Optional<BackgroundJob> current(Long jobId, String expectedLeaseOwner) {
    return jobStore
        .findForUpdate(jobId)
        .filter(job -> Objects.equals(job.getLeaseOwner(), expectedLeaseOwner))
        .filter(job -> !finished(job));
  }

  private boolean finished(BackgroundJob job) {
    return job.getState() == BackgroundJobState.COMPLETED
        || job.getState() == BackgroundJobState.TERMINAL
        || refusedForDismissal(job);
  }

  private static boolean refusedForDismissal(BackgroundJob job) {
    return inboxState(job) == InboxState.DISMISSED && !job.getType().runsAfterDismissal();
  }

  private static void terminalDismissed(BackgroundJob job) {
    job.setState(BackgroundJobState.TERMINAL);
    job.setTerminalReason("DISMISSED");
    clearLease(job);
  }

  private boolean acceptedParsing(BackgroundJob job) {
    InboxState state = inboxState(job);
    return job.getType().isParsing()
        && (state == InboxState.READY
            || state == InboxState.SAVED
            || state == InboxState.SAVE_PENDING);
  }

  private static InboxState inboxState(BackgroundJob job) {
    return job.findInboxItem().map(InboxItem::getState).orElse(null);
  }

  private void publishQueued(BackgroundJob job) {
    events.publishEvent(
        job.findInboxItem()
            .<Object>map(item -> new InboxItemQueued(item.getId()))
            .orElseGet(() -> new BackgroundJobQueued(job.getId())));
  }

  private void completed(BackgroundJob job, JobExecutionResult result) {
    job.setState(BackgroundJobState.COMPLETED);
    clearLease(job);
    job.setLastError(null);
    job.setTerminalReason(null);
    InboxItem item = job.getInboxItem();
    if (item == null) {
      return;
    }
    if (StringUtils.isNotBlank(result.immutableSourceId())) {
      item.setImmutableSourceId(result.immutableSourceId());
    }
    if (job.getType().isExternalSync()) {
      item.setExternalSyncState(ExternalSyncState.SUCCEEDED);
      item.setErrorMessage(null);
    }
  }

  private void markProcessing(BackgroundJob job) {
    InboxItem item = job.getInboxItem();
    if (item == null) {
      return;
    }
    if (job.getType().isParsing()) {
      InboxStateMachine.transition(item, InboxState.PROCESSING);
      item.setRawStatus("PROCESSING");
      item.setErrorMessage(null);
    } else if (job.getType().isExternalSync()) {
      item.setExternalSyncState(ExternalSyncState.PROCESSING);
      item.setErrorMessage(null);
    }
  }

  private void requeued(BackgroundJob job) {
    InboxItem item = job.getInboxItem();
    if (item == null) {
      return;
    }
    if (job.getType().isParsing()) {
      InboxStateMachine.transition(item, InboxState.QUEUED);
      item.setRawStatus("PROCESSING");
      item.setErrorMessage(null);
    } else if (job.getType().isExternalSync()) {
      item.setExternalSyncState(ExternalSyncState.PENDING);
      item.setErrorMessage(job.getLastError());
    }
  }

  private void terminal(BackgroundJob job, String reason, String message, boolean manual) {
    job.setState(BackgroundJobState.TERMINAL);
    clearLease(job);
    job.setTerminalReason(reason);
    job.setLastError(message);
    InboxItem item = job.getInboxItem();
    if (item == null) {
      return;
    }
    if (job.getType().isParsing()) {
      InboxStateMachine.transition(item, InboxState.FAILED);
      item.setRawStatus("FAILED");
      item.setErrorMessage(message);
    } else if (job.getType().isExternalSync()) {
      item.setExternalSyncState(
          manual ? ExternalSyncState.MANUAL_REVIEW : ExternalSyncState.FAILED);
      item.setErrorMessage(message);
    }
  }

  private BackgroundJob ensureJob(
      InboxItem item, BackgroundJobType type, Integer targetYear, boolean reset) {
    String idempotencyKey = type.name().toLowerCase() + ":" + item.getId();
    BackgroundJob job =
        jobStore
            .findByIdempotencyKey(idempotencyKey)
            .flatMap(existing -> jobStore.findForUpdate(existing.getId()))
            .orElseGet(() ->
                BackgroundJob.builder()
                    .inboxItem(item)
                    .type(type)
                    .idempotencyKey(idempotencyKey)
                    .state(BackgroundJobState.AVAILABLE)
                    .attempts(0)
                    .maxAttempts(DEFAULT_MAX_ATTEMPTS)
                    .availableAt(now())
                    .build());
    if (reset && job.getState() != BackgroundJobState.LEASED) {
      resetGeneration(job, now());
    }
    job.setTargetYear(targetYear);
    return jobStore.save(job);
  }

  private void save(BackgroundJob job) {
    job.findInboxItem().ifPresent(inboxItemStore::save);
    jobStore.save(job);
  }

  private static void clearLease(BackgroundJob job) {
    job.setLeaseOwner(null);
    job.setLeaseExpiresAt(null);
  }

  private String errorMessage(Throwable failure) {
    return errorMessage(
        StringUtils.defaultIfBlank(failure.getMessage(), failure.getClass().getName()));
  }

  private String errorMessage(String message) {
    return StringUtils.left(StringUtils.defaultIfBlank(message, "Job execution failed"), 2000);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  private record Execution(Long jobId, String leaseOwner) {}
}
