package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.model.ExpenseSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DurableBackgroundJobWorker {

  private final BackgroundJobService jobService;
  private final IntakeJobDispatcher dispatcher;
  private final String workerId = "bookie-" + UUID.randomUUID();

  @Value("${bookie.intake.worker.enabled:true}")
  private boolean enabled;

  @Value("${bookie.intake.worker.lease-seconds:300}")
  private long leaseSeconds;

  @Value("${bookie.intake.worker.max-jobs-per-poll:10}")
  private int maxJobsPerPoll;

  @Value(
      "${bookie.intake.worker.allowed-job-types:"
          + "TRANSLATE_OUTLOOK_ID,PARSE_OUTLOOK,PARSE_RECEIPT}")
  private Set<BackgroundJobType> allowedJobTypes;

  @Scheduled(
      initialDelayString = "${bookie.intake.worker.initial-delay-ms:5000}",
      fixedDelayString = "${bookie.intake.worker.poll-interval-ms:5000}")
  public void poll() {
    if (!canClaimAny()) {
      return;
    }
    jobService.recoverExpiredLeases();
    List<BackgroundJob> claimedJobs = new ArrayList<>();
    for (int count = 0; count < maxJobsPerPoll; count++) {
      Optional<BackgroundJob> claimed =
          jobService.claimNext(workerId, Duration.ofSeconds(leaseSeconds), allowedJobTypes);
      if (claimed.isEmpty()) {
        break;
      }
      claimedJobs.add(claimed.get());
    }
    executeClaimed(claimedJobs);
  }

  public void runAvailableJob(Long jobId) {
    if (!canClaimAny()) {
      return;
    }
    jobService
        .claim(jobId, workerId, Duration.ofSeconds(leaseSeconds), allowedJobTypes)
        .ifPresent(this::execute);
  }

  public void runAvailableForLegacy(LegacyPendingKey key, BackgroundJobType type) {
    if (!canExecute(type)) {
      return;
    }
    jobService.findLatest(key, type).map(BackgroundJob::getId).ifPresent(this::runAvailableJob);
  }

  public void runAvailableForSource(
      ExpenseSource origin, String legacySourceId, BackgroundJobType type) {
    if (!canExecute(type)) {
      return;
    }
    jobService
        .findAvailableForSource(origin, legacySourceId, type)
        .map(BackgroundJob::getId)
        .ifPresent(this::runAvailableJob);
  }

  private boolean canClaimAny() {
    return enabled && !allowedJobTypes.isEmpty();
  }

  private boolean canExecute(BackgroundJobType type) {
    return canClaimAny() && allowedJobTypes.contains(type);
  }

  private void execute(BackgroundJob job) {
    try {
      finish(job, JobExecutionOutcome.succeeded(dispatcher.execute(job)));
    } catch (Exception failure) {
      finish(job, JobExecutionOutcome.failed(failure));
    }
  }

  private void executeClaimed(List<BackgroundJob> jobs) {
    List<BackgroundJob> translations =
        jobs.stream()
            .filter(job -> job.getType() == BackgroundJobType.TRANSLATE_OUTLOOK_ID)
            .toList();
    if (translations.size() == 1) {
      execute(translations.getFirst());
    } else if (!translations.isEmpty()) {
      executeBatch(translations);
    }
    jobs.stream()
        .filter(job -> job.getType() != BackgroundJobType.TRANSLATE_OUTLOOK_ID)
        .forEach(this::execute);
  }

  private void executeBatch(List<BackgroundJob> jobs) {
    Map<Long, JobExecutionOutcome> outcomes;
    try {
      outcomes = dispatcher.executeBatch(jobs);
    } catch (Exception batchFailure) {
      jobs.forEach(job -> finish(job, JobExecutionOutcome.failed(batchFailure)));
      return;
    }
    for (BackgroundJob job : jobs) {
      JobExecutionOutcome outcome = outcomes.get(job.getId());
      if (outcome == null) {
        outcome =
            JobExecutionOutcome.failed(
                JobExecutionException.terminal(
                    "Dispatcher omitted a leased job from its batch result", null));
      }
      finish(job, outcome);
    }
  }

  private void finish(BackgroundJob job, JobExecutionOutcome outcome) {
    if (!outcome.successful()) {
      log.warn(
          "Background job {} ({}) failed on attempt {}",
          job.getId(),
          job.getType(),
          job.getAttempts(),
          outcome.failure());
      try {
        jobService.fail(job.getId(), workerId, outcome.failure());
      } catch (Exception statusFailure) {
        log.error(
            "Could not persist failure state for background job {}", job.getId(), statusFailure);
      }
      return;
    }
    try {
      jobService.complete(job.getId(), workerId, outcome.result());
    } catch (Exception statusFailure) {
      log.warn(
          "Could not persist completion state for background job {}", job.getId(), statusFailure);
      try {
        jobService.fail(
            job.getId(),
            workerId,
            JobExecutionException.retryable(
                "Could not record completed background job", statusFailure));
      } catch (Exception failureUpdateFailure) {
        log.error(
            "Could not preserve failure evidence for background job {}",
            job.getId(),
            failureUpdateFailure);
      }
    }
  }
}
