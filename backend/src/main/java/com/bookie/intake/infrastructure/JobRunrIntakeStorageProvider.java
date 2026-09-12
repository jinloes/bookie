package com.bookie.intake.infrastructure;

import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.DurableBackgroundJobWorker.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.BackgroundJobServerStatus;
import org.jobrunr.storage.JobRunrMetadata;
import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.Page;
import org.jobrunr.storage.RecurringJobsResult;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.StorageProviderUtils.DatabaseOptions;
import org.jobrunr.storage.listeners.StorageProviderChangeListener;
import org.jobrunr.storage.navigation.AmountRequest;
import org.jobrunr.storage.navigation.PageRequest;

/** Server-only candidate view; scheduler, relay and restore keep the unfiltered storage. */
@RequiredArgsConstructor
public class JobRunrIntakeStorageProvider implements StorageProvider {
  private final StorageProvider raw;
  private final Settings settings;

  @Override
  public List<Job> getJobList(StateName state, AmountRequest request) {
    if (state == StateName.ENQUEUED) {
      return candidates(state, request, prefix -> raw.getJobList(state, prefix));
    }
    return raw.getJobList(state, request);
  }

  @Override
  public List<Job> getJobList(StateName state, Instant cutoff, AmountRequest request) {
    if (state == StateName.PROCESSING) {
      return candidates(state, request, prefix -> raw.getJobList(state, cutoff, prefix));
    }
    return raw.getJobList(state, cutoff, request);
  }

  @Override
  public List<Job> getScheduledJobs(Instant cutoff, AmountRequest request) {
    return candidates(StateName.SCHEDULED, request, prefix -> raw.getScheduledJobs(cutoff, prefix));
  }

  // Inherit StorageProvider.getJobsToProcess: SQL's override bypasses candidate reads.
  private List<Job> candidates(
      StateName state, AmountRequest request, Function<AmountRequest, List<Job>> read) {
    int limit = request.getLimit();
    if (limit < 0) {
      throw new IllegalArgumentException("Intake candidate limit must not be negative");
    }
    if (limit == 0) {
      return List.of();
    }
    int ceiling = Math.toIntExact(raw.countJobs(state));
    if (ceiling < 0) {
      throw new IllegalStateException("Intake candidate count must not be negative");
    }
    if (ceiling == 0) {
      return List.of();
    }
    int size = Math.min(ceiling, Math.max(64, limit));
    while (true) {
      List<Job> prefix = read.apply(new AmountRequest(request.getOrder(), size));
      List<Job> allowed =
          prefix.stream()
              .filter(
                  job ->
                      DurableBackgroundJobWorker.invocationType(job)
                          .map(settings::allows)
                          .orElse(true))
              .limit(limit)
              .toList();
      if (allowed.size() == limit || prefix.size() < size || size == ceiling) {
        return allowed;
      }
      // Re-read whole prefixes: timestamp ties cannot safely be traversed with offset pages.
      size = (int) Math.min(ceiling, (long) size * 2);
    }
  }

  @Override
  public StorageProviderInfo getStorageProviderInfo() {
    return raw.getStorageProviderInfo();
  }

  @Override
  public void setJobMapper(JobMapper mapper) {
    raw.setJobMapper(mapper);
  }

  @Override
  public void setUpStorageProvider(DatabaseOptions options) {
    raw.setUpStorageProvider(options);
  }

  @Override
  public void addJobStorageOnChangeListener(StorageProviderChangeListener listener) {
    raw.addJobStorageOnChangeListener(listener);
  }

  @Override
  public void removeJobStorageOnChangeListener(StorageProviderChangeListener listener) {
    raw.removeJobStorageOnChangeListener(listener);
  }

  @Override
  public void announceBackgroundJobServer(BackgroundJobServerStatus status) {
    raw.announceBackgroundJobServer(status);
  }

  @Override
  public boolean signalBackgroundJobServerAlive(BackgroundJobServerStatus status) {
    return raw.signalBackgroundJobServerAlive(status);
  }

  @Override
  public void signalBackgroundJobServerStopped(BackgroundJobServerStatus status) {
    raw.signalBackgroundJobServerStopped(status);
  }

  @Override
  public List<BackgroundJobServerStatus> getBackgroundJobServers() {
    return raw.getBackgroundJobServers();
  }

  @Override
  public UUID getLongestRunningBackgroundJobServerId() {
    return raw.getLongestRunningBackgroundJobServerId();
  }

  @Override
  public int removeTimedOutBackgroundJobServers(Instant cutoff) {
    return raw.removeTimedOutBackgroundJobServers(cutoff);
  }

  @Override
  public void saveMetadata(JobRunrMetadata metadata) {
    raw.saveMetadata(metadata);
  }

  @Override
  public List<JobRunrMetadata> getMetadata(String name) {
    return raw.getMetadata(name);
  }

  @Override
  public JobRunrMetadata getMetadata(String name, String owner) {
    return raw.getMetadata(name, owner);
  }

  @Override
  public void deleteMetadata(String name) {
    raw.deleteMetadata(name);
  }

  @Override
  public void deleteMetadata(String name, String owner) {
    raw.deleteMetadata(name, owner);
  }

  @Override
  public Job save(Job job) {
    return raw.save(job);
  }

  @Override
  public List<Job> save(List<Job> jobs) {
    return raw.save(jobs);
  }

  @Override
  public Job getJobById(UUID id) {
    return raw.getJobById(id);
  }

  @Override
  public Job getJobById(JobId id) {
    return raw.getJobById(id);
  }

  @Override
  public long countJobs(StateName state) {
    return raw.countJobs(state);
  }

  @Override
  public Page<Job> getJobs(StateName state, PageRequest request) {
    return raw.getJobs(state, request);
  }

  @Override
  public List<Job> getCarbonAwareJobList(Instant cutoff, AmountRequest request) {
    return raw.getCarbonAwareJobList(cutoff, request);
  }

  @Override
  public Page<Job> getScheduledJobs(Instant cutoff, PageRequest request) {
    return raw.getScheduledJobs(cutoff, request);
  }

  @Override
  public int deletePermanently(UUID id) {
    return raw.deletePermanently(id);
  }

  @Override
  public int deleteJobsPermanently(StateName state, Instant cutoff) {
    return raw.deleteJobsPermanently(state, cutoff);
  }

  @Override
  public Set<String> getDistinctJobSignatures(StateName... states) {
    return raw.getDistinctJobSignatures(states);
  }

  @Override
  public Instant getRecurringJobLatestScheduledInstant(String id, StateName... states) {
    return raw.getRecurringJobLatestScheduledInstant(id, states);
  }

  @Override
  public RecurringJob saveRecurringJob(RecurringJob job) {
    return raw.saveRecurringJob(job);
  }

  @Override
  public RecurringJobsResult getRecurringJobs() {
    return raw.getRecurringJobs();
  }

  @Override
  public boolean recurringJobsUpdated(Long updatedAt) {
    return raw.recurringJobsUpdated(updatedAt);
  }

  @Override
  public int deleteRecurringJob(String id) {
    return raw.deleteRecurringJob(id);
  }

  @Override
  public JobStats getJobStats() {
    return raw.getJobStats();
  }

  @Override
  public void publishTotalAmountOfSucceededJobs(int amount) {
    raw.publishTotalAmountOfSucceededJobs(amount);
  }

  @Override
  public void close() {
    raw.close();
  }

  @Override
  public void validatePollInterval(Duration interval) {
    raw.validatePollInterval(interval);
  }

  @Override
  public void validateRecurringJobInterval(Duration interval) {
    raw.validateRecurringJobInterval(interval);
  }
}
