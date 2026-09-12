package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.model.ExpenseSource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.JobRunrException;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.JobNotFoundException;
import org.jobrunr.storage.StorageProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DurableBackgroundJobWorker {

  private final BackgroundJobService jobService;
  private final IntakeJobDispatcher dispatcher;
  private final JobScheduler scheduler;
  private final StorageProvider storage;
  private final Settings settings;
  private final Clock clock;
  private volatile Instant publicationStartsAt;

  public void ready() {
    publicationStartsAt = clock.instant().plusMillis(settings.initialDelayMillis());
  }

  public void stop() {
    publicationStartsAt = null;
  }

  @Scheduled(fixedDelayString = "${bookie.intake.worker.poll-interval-ms:5000}")
  public synchronized void relay() {
    Instant startsAt = publicationStartsAt;
    if (startsAt == null || clock.instant().isBefore(startsAt)) {
      return;
    }
    publish();
  }

  public synchronized void runAvailableJob(Long jobId) {
    if (publicationStartsAt != null) {
      publish();
    }
  }

  public void runAvailableForLegacy(LegacyPendingKey key, BackgroundJobType type) {
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

  private void publish() {
    if (!settings.enabled() || settings.allowedTypes().isEmpty()) {
      return;
    }
    jobService.bindDue(settings.maxJobsPerPoll(), settings.allowedTypes());
    for (UUID executionId : jobService.activeExecutionIds()) {
      Map<Long, Long> versions =
          jobService.binding(executionId).stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      BackgroundJob::getId, BackgroundJob::getVersion));
      Job engineJob;
      try {
        engineJob = storage.getJobById(executionId);
      } catch (JobNotFoundException missing) {
        engineJob = null;
      }
      jobService.project(executionId, engineJob, versions);
      if (engineJob != null) {
        continue;
      }
      List<BackgroundJob> binding = jobService.binding(executionId);
      if (binding.isEmpty() || binding.stream().anyMatch(BackgroundJob::isExecutionStarted)) {
        continue;
      }
      Optional<BackgroundJob> eligible =
          binding.stream()
              .filter(job -> job.getState() == BackgroundJobState.AVAILABLE)
              .filter(job -> job.getInboxItem().getState() != InboxState.DISMISSED)
              .filter(job -> settings.allows(job.getType()))
              .findFirst();
      if (eligible.isPresent()) {
        String type = eligible.get().getType().name();
        scheduler.<DurableBackgroundJobWorker>enqueue(
            executionId, worker -> worker.executeV1(type, JobContext.Null));
      }
    }
  }

  public void executeV1(String type, JobContext context) {
    BackgroundJobType jobType =
        parseType(type).orElseThrow(() -> invalidInvocation("Unsupported intake type"));
    if (context == null || context == JobContext.Null) {
      throw invalidInvocation("Intake requires an engine JobContext");
    }
    if (publicationStartsAt == null || !settings.allows(jobType)) {
      throw invalidInvocation("Intake execution is not enabled for this callback");
    }
    UUID executionId = context.getJobId();
    if (executionId == null || context.getJobState() != StateName.PROCESSING) {
      throw invalidInvocation("Intake requires a processing engine execution");
    }
    Job persisted = storage.getJobById(executionId);
    if (persisted.getState() != StateName.PROCESSING
        || invocationType(persisted).filter(jobType::equals).isEmpty()) {
      throw invalidInvocation("Intake callback does not match persisted execution");
    }
    List<BackgroundJob> jobs =
        jobService.start(executionId, jobType, context.amountOfFailures() + 1);
    boolean retry = false;
    if (jobs.size() > 1 && jobType == BackgroundJobType.TRANSLATE_OUTLOOK_ID) {
      Map<Long, JobExecutionOutcome> outcomes;
      try {
        outcomes = dispatcher.executeBatch(jobs);
      } catch (DataAccessException | TransactionException infrastructureFailure) {
        throw infrastructureFailure;
      } catch (Exception failure) {
        outcomes =
            jobs.stream()
                .collect(
                    java.util.stream.Collectors.toMap(
                        BackgroundJob::getId, job -> JobExecutionOutcome.failed(failure)));
      }
      for (BackgroundJob job : jobs) {
        JobExecutionOutcome outcome = outcomes == null ? null : outcomes.get(job.getId());
        if (outcome == null) {
          outcome =
              JobExecutionOutcome.failed(
                  JobExecutionException.terminal("Dispatcher omitted a bound batch member", null));
        }
        retry |= jobService.finish(job.getId(), executionId, outcome);
      }
    } else {
      for (BackgroundJob job : jobs) {
        JobExecutionOutcome outcome;
        BackgroundJobService.enterExecution(job);
        try {
          outcome = JobExecutionOutcome.succeeded(dispatcher.execute(job));
        } catch (DataAccessException | TransactionException infrastructureFailure) {
          throw infrastructureFailure;
        } catch (Exception failure) {
          outcome = JobExecutionOutcome.failed(failure);
        } finally {
          BackgroundJobService.leaveExecution();
        }
        retry |= jobService.finish(job.getId(), executionId, outcome);
      }
    }
    if (retry) {
      throw JobExecutionException.retryable("Intake execution has retryable members", null);
    }
  }

  public static Optional<BackgroundJobType> invocationType(Job job) {
    JobDetails details = job.getJobDetails();
    if (!DurableBackgroundJobWorker.class.getName().equals(details.getClassName())
        || !"executeV1".equals(details.getMethodName())
        || details.hasStaticFieldName()
        || details.getJobParameters().size() != 2
        || !String.class.getName().equals(details.getJobParameters().getFirst().getClassName())
        || !JobContext.class.getName().equals(details.getJobParameters().get(1).getClassName())
        || !(details.getJobParameters().getFirst().getObject() instanceof String type)) {
      return Optional.empty();
    }
    return parseType(type);
  }

  private static Optional<BackgroundJobType> parseType(String type) {
    return java.util.Arrays.stream(BackgroundJobType.values())
        .filter(candidate -> candidate.name().equals(type))
        .findFirst();
  }

  private static JobRunrException invalidInvocation(String message) {
    return JobRunrException.problematicException(message, null);
  }

  @Builder
  public record Settings(
      boolean enabled,
      Set<BackgroundJobType> allowedTypes,
      long initialDelayMillis,
      long pollIntervalMillis,
      int maxJobsPerPoll,
      int heartbeatTimeoutMultiplier) {
    public Settings {
      allowedTypes = Set.copyOf(allowedTypes);
      if (initialDelayMillis < 0
          || pollIntervalMillis <= 0
          || maxJobsPerPoll <= 0
          || heartbeatTimeoutMultiplier < 4) {
        throw new IllegalArgumentException("Invalid intake worker timing or capacity settings");
      }
    }

    public boolean allows(BackgroundJobType type) {
      return enabled && allowedTypes.contains(type);
    }
  }
}
