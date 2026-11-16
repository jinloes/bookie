package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.ledger.domain.ExpenseSource;
import com.bookie.util.WriteFreezeState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DurableBackgroundJobWorker {

  private final BackgroundJobService jobService;
  private final IntakeJobDispatcher dispatcher;
  private final Settings settings;
  private final Clock clock;
  private final WriteFreezeState writeFreeze;
  private volatile Instant executionStartsAt;

  public void ready() {
    executionStartsAt = clock.instant().plusMillis(settings.initialDelayMillis());
  }

  public void stop() {
    executionStartsAt = null;
  }

  /** Continuous outbox polling; in on-demand mode only explicit/triggered drains run passes. */
  @Scheduled(fixedDelayString = "${bookie.intake.worker.poll-interval-ms:5000}")
  public synchronized void relay() {
    Instant startsAt = executionStartsAt;
    if (settings.onDemand()
        || startsAt == null
        || clock.instant().isBefore(startsAt)
        || writeFreeze.isFrozen()) {
      return;
    }
    executeDueBatch();
  }

  public synchronized void runAvailableJob(Long jobId) {
    if (executionStartsAt != null && !writeFreeze.isFrozen()) {
      executeDueBatch();
    }
  }

  /**
   * One drain pass. Ignores write freeze so pre-migration/rollback drains finish in-flight work.
   */
  public synchronized void relayNow() {
    if (executionStartsAt != null) {
      executeDueBatch();
    }
  }

  public boolean hasDueOrBoundWork() {
    return settings.enabled() && jobService.hasDueOrBoundWork(settings.allowedTypes());
  }

  public void runAvailableForInboxItem(Long inboxItemId, BackgroundJobType type) {
    if (settings.allows(type)) {
      runAvailableJob(null);
    }
  }

  public void runAvailableForSource(
      ExpenseSource origin, String legacySourceId, BackgroundJobType type) {
    if (settings.allows(type)) {
      runAvailableJob(null);
    }
  }

  private void executeDueBatch() {
    if (!settings.enabled() || settings.allowedTypes().isEmpty()) {
      return;
    }
    List<BackgroundJob> claimed =
        jobService.claimDue(
            settings.maxJobsPerPoll(),
            settings.allowedTypes(),
            Duration.ofSeconds(settings.leaseSeconds()));
    if (claimed.isEmpty()) {
      return;
    }

    List<BackgroundJob> translations = new ArrayList<>();
    List<BackgroundJob> singletons = new ArrayList<>();
    for (BackgroundJob job : claimed) {
      if (job.getType() == BackgroundJobType.TRANSLATE_OUTLOOK_ID) {
        translations.add(job);
      } else {
        singletons.add(job);
      }
    }

    if (translations.size() > 1) {
      executeTranslationBatch(translations);
    } else {
      singletons.addAll(0, translations);
    }

    for (BackgroundJob job : singletons) {
      executeSingle(job);
    }
  }

  private void executeTranslationBatch(List<BackgroundJob> jobs) {
    Map<Long, JobExecutionOutcome> outcomes;
    try {
      outcomes = dispatcher.executeBatch(jobs);
    } catch (Exception failure) {
      outcomes =
          jobs.stream()
              .collect(
                  Collectors.toMap(
                      BackgroundJob::getId, ignored -> JobExecutionOutcome.failed(failure)));
    }
    for (BackgroundJob job : jobs) {
      JobExecutionOutcome outcome = outcomes == null ? null : outcomes.get(job.getId());
      if (outcome == null) {
        outcome =
            JobExecutionOutcome.failed(
                JobExecutionException.terminal("Dispatcher omitted a bound batch member", null));
      }
      jobService.finish(job.getId(), job.getLeaseOwner(), outcome);
    }
  }

  private void executeSingle(BackgroundJob job) {
    JobExecutionOutcome outcome;
    BackgroundJobService.enterExecution(job);
    try {
      outcome = JobExecutionOutcome.succeeded(dispatcher.execute(job));
    } catch (Exception failure) {
      outcome = JobExecutionOutcome.failed(failure);
    } finally {
      BackgroundJobService.leaveExecution();
    }
    jobService.finish(job.getId(), job.getLeaseOwner(), outcome);
  }

  @Builder
  public record Settings(
      boolean enabled,
      Set<BackgroundJobType> allowedTypes,
      long initialDelayMillis,
      long pollIntervalMillis,
      int maxJobsPerPoll,
      long leaseSeconds,
      boolean onDemand) {
    public Settings {
      allowedTypes = Set.copyOf(allowedTypes);
      if (initialDelayMillis < 0
          || pollIntervalMillis <= 0
          || maxJobsPerPoll <= 0
          || leaseSeconds <= 0) {
        throw new IllegalArgumentException("Invalid intake worker timing or capacity settings");
      }
    }

    public boolean allows(BackgroundJobType type) {
      return enabled && allowedTypes.contains(type);
    }
  }
}
