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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.FailedState;
import org.jobrunr.jobs.states.ScheduledState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BackgroundJobService {

  private static final int GENERATION_EXECUTIONS = 11;
  private static final ThreadLocal<Execution> CURRENT_EXECUTION = new ThreadLocal<>();

  private final BackgroundJobStore jobStore;
  private final InboxItemStore inboxItemStore;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  @Transactional
  public void bindDue(int limit, Set<BackgroundJobType> allowedTypes) {
    if (allowedTypes.isEmpty()) {
      return;
    }
    UUID translationExecution = UUID.randomUUID();
    for (BackgroundJob job : jobStore.findUnboundDue(now(), limit, allowedTypes)) {
      if (finished(job)) {
        continue;
      }
      if (acceptedParsing(job)) {
        completed(job, JobExecutionResult.completed());
      } else if (job.getAttempts() >= job.getMaxAttempts()) {
        terminal(job, "MAX_ATTEMPTS", "Legacy attempts exhausted", false);
      } else {
        job.setExecutionAttemptBase(job.getAttempts());
        job.setExecutionPreviousMaxAttempts(job.getMaxAttempts());
        job.setMaxAttempts(Math.addExact(job.getAttempts(), GENERATION_EXECUTIONS));
        job.setExecutionId(
            job.getType() == BackgroundJobType.TRANSLATE_OUTLOOK_ID
                ? translationExecution
                : UUID.randomUUID());
        job.setExecutionStarted(false);
        job.setState(BackgroundJobState.AVAILABLE);
        clearLease(job);
        requeued(job);
      }
      save(job);
    }
  }

  @Transactional(readOnly = true)
  public List<UUID> activeExecutionIds() {
    return jobStore.findActiveExecutionIds();
  }

  @Transactional
  public List<BackgroundJob> binding(UUID executionId) {
    return jobStore.findByExecutionId(executionId);
  }

  @Transactional
  public List<BackgroundJob> start(UUID executionId, BackgroundJobType type, int attemptOrdinal) {
    List<BackgroundJob> members = jobStore.findByExecutionId(executionId);
    if (members.stream().anyMatch(job -> job.getType() != type)) {
      throw org.jobrunr.JobRunrException.problematicException(
          "Intake execution type does not match its durable binding", null);
    }
    List<BackgroundJob> started = new ArrayList<>();
    for (BackgroundJob job : members) {
      if (finished(job)) {
        continue;
      }
      if (acceptedParsing(job)) {
        completed(job, JobExecutionResult.completed());
      } else {
        job.setExecutionStarted(true);
        job.setAttempts(Math.addExact(job.getExecutionAttemptBase(), attemptOrdinal));
        job.setState(BackgroundJobState.LEASED);
        clearLease(job);
        markProcessing(job);
        started.add(job);
      }
      save(job);
    }
    return started;
  }

  @Transactional
  public boolean finish(Long jobId, UUID executionId, JobExecutionOutcome outcome) {
    Optional<BackgroundJob> current = current(jobId, executionId);
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
    boolean retryable =
        failure instanceof JobExecutionException executionFailure
            && executionFailure.getKind() == JobExecutionException.FailureKind.RETRYABLE;
    if (retryable) {
      job.setState(BackgroundJobState.AVAILABLE);
      clearLease(job);
      job.setLastError(errorMessage(failure));
      job.setTerminalReason(null);
      requeued(job);
    } else {
      boolean manual =
          failure instanceof JobExecutionException executionFailure
              && executionFailure.getKind() == JobExecutionException.FailureKind.MANUAL_REVIEW;
      terminal(job, manual ? "MANUAL_REVIEW" : "TERMINAL", errorMessage(failure), manual);
    }
    save(job);
    return retryable;
  }

  @Transactional
  public void project(UUID executionId, Job engineJob, Map<Long, Long> expectedVersions) {
    for (BackgroundJob job : jobStore.findByExecutionId(executionId)) {
      if (finished(job) || !Objects.equals(expectedVersions.get(job.getId()), job.getVersion())) {
        continue;
      }
      if (acceptedParsing(job)) {
        completed(job, JobExecutionResult.completed());
      } else if (engineJob == null) {
        if (!job.isExecutionStarted()) {
          continue;
        }
        terminal(job, "MANUAL_REVIEW", "Started engine execution is missing", true);
      } else {
        int failures = Math.toIntExact(engineJob.getJobStatesOfType(FailedState.class).count());
        switch (engineJob.getState()) {
          case ENQUEUED -> {
            continue;
          }
          case SCHEDULED -> {
            job.setState(BackgroundJobState.AVAILABLE);
            job.setAttempts(Math.addExact(job.getExecutionAttemptBase(), failures));
            ScheduledState scheduled = engineJob.getJobState();
            job.setAvailableAt(
                LocalDateTime.ofInstant(scheduled.getScheduledAt(), clock.getZone()));
            engineJob
                .getLastJobStateOfType(FailedState.class)
                .ifPresent(
                    failure -> job.setLastError(errorMessage(failure.getExceptionMessage())));
            clearLease(job);
            requeued(job);
          }
          case PROCESSING -> {
            job.setState(BackgroundJobState.LEASED);
            job.setAttempts(Math.addExact(job.getExecutionAttemptBase(), failures + 1));
            clearLease(job);
          }
          case FAILED -> {
            FailedState failed = engineJob.getJobState();
            job.setAttempts(Math.addExact(job.getExecutionAttemptBase(), failures));
            boolean exhausted = !failed.mustNotRetry() && failures >= GENERATION_EXECUTIONS;
            terminal(
                job,
                exhausted ? "MAX_ATTEMPTS" : "MANUAL_REVIEW",
                errorMessage(failed.getExceptionMessage()),
                !exhausted);
          }
          default ->
              terminal(
                  job,
                  "MANUAL_REVIEW",
                  "Unfinished intake has engine state " + engineJob.getState(),
                  true);
        }
      }
      save(job);
    }
  }

  @Transactional
  public BackgroundJob retry(Long jobId) {
    BackgroundJob job =
        jobStore
            .findForUpdate(jobId)
            .orElseThrow(
                () ->
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
    job.setExecutionId(null);
    job.setExecutionStarted(false);
    job.setExecutionAttemptBase(0);
    job.setExecutionPreviousMaxAttempts(null);
    job.setState(BackgroundJobState.AVAILABLE);
    job.setAttempts(0);
    job.setMaxAttempts(GENERATION_EXECUTIONS);
    job.setAvailableAt(now);
    clearLease(job);
    job.setLastError(null);
    job.setTerminalReason(null);
    job.findInboxItem().ifPresent(item -> item.setErrorMessage(null));
  }

  // Parsing commits its proposal inside the dispatcher. Carry the generation across that
  // transaction so the proposal writer can reject a stale write before it commits.
  static void enterExecution(BackgroundJob job) {
    CURRENT_EXECUTION.set(new Execution(job.getId(), job.getExecutionId()));
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
        current(execution.jobId(), execution.executionId())
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

  /**
   * Makes the item's job of this type available for a fresh generation, creating it when absent. A
   * leased job keeps its lease; its next generation starts when that execution finishes.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public BackgroundJob schedule(InboxItem item, BackgroundJobType type, Integer targetYear) {
    return ensureJob(item, type, targetYear, true);
  }

  /** Creates the item's job of this type if absent, leaving an existing generation untouched. */
  @Transactional(propagation = Propagation.MANDATORY)
  public BackgroundJob scheduleIfAbsent(InboxItem item, BackgroundJobType type) {
    return ensureJob(item, type, null, false);
  }

  /**
   * Schedules the move of a manually entered receipt into its tax-year folder. Such a transaction
   * has no inbox item, so the job names the OneDrive item itself; it is kicked after the caller's
   * transaction commits ({@link BackgroundJobQueued}).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public BackgroundJob scheduleReceiptMove(
      Long transactionId, String receiptExternalId, int targetYear) {
    String idempotencyKey = "move_receipt:transaction:" + transactionId;
    BackgroundJob job =
        jobStore
            .findByIdempotencyKey(idempotencyKey)
            .orElseGet(
                () ->
                    jobStore.save(
                        BackgroundJob.builder()
                            .type(BackgroundJobType.MOVE_RECEIPT)
                            .sourceExternalId(receiptExternalId)
                            .targetYear(targetYear)
                            .idempotencyKey(idempotencyKey)
                            .state(BackgroundJobState.AVAILABLE)
                            .attempts(0)
                            .maxAttempts(GENERATION_EXECUTIONS)
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
        .flatMap(
            item ->
                jobStore.findByInboxItemId(item.getId()).stream()
                    .filter(job -> job.getType() == type)
                    .filter(job -> job.getState() == BackgroundJobState.AVAILABLE)
                    .findFirst());
  }

  private Optional<BackgroundJob> current(Long jobId, UUID executionId) {
    return jobStore
        .findForUpdate(jobId)
        .filter(job -> Objects.equals(job.getExecutionId(), executionId))
        .filter(job -> !finished(job));
  }

  private boolean finished(BackgroundJob job) {
    return job.getState() == BackgroundJobState.COMPLETED
        || job.getState() == BackgroundJobState.TERMINAL
        || inboxState(job) == InboxState.DISMISSED;
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
            .orElseGet(
                () ->
                    BackgroundJob.builder()
                        .inboxItem(item)
                        .type(type)
                        .idempotencyKey(idempotencyKey)
                        .state(BackgroundJobState.AVAILABLE)
                        .attempts(0)
                        .maxAttempts(GENERATION_EXECUTIONS)
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
    return StringUtils.left(StringUtils.defaultIfBlank(message, "Engine execution failed"), 2000);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  private record Execution(Long jobId, UUID executionId) {}
}
