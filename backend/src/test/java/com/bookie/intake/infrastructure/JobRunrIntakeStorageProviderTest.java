package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.domain.BackgroundJobType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jobrunr.JobRunrException;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.JobParameter;
import org.jobrunr.jobs.filters.JobFilterUtils;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.jobs.states.FailedState;
import org.jobrunr.jobs.states.ProcessingState;
import org.jobrunr.jobs.states.ScheduledState;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.ConcurrentJobModificationException;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.navigation.AmountRequest;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JobRunrIntakeStorageProviderTest {
  private final DurableBackgroundJobWorker.Settings settings =
      DurableBackgroundJobWorker.Settings.builder()
          .enabled(true)
          .allowedTypes(Set.of(BackgroundJobType.MOVE_RECEIPT))
          .initialDelayMillis(0)
          .pollIntervalMillis(5000)
          .maxJobsPerPoll(10)
          .heartbeatTimeoutMultiplier(4)
          .build();

  @Nested
  class Election {
    @Test
    void gatePreservesBlockedCandidateBeforeNativeClaimAndAllowedWorkStillProcesses() {
      try (InMemoryStorageProvider storage = new InMemoryStorageProvider()) {
        storage.setJobMapper(new JobMapper(new JacksonJsonMapper()));
        JobRunrIntakeStorageProvider gate = new JobRunrIntakeStorageProvider(storage, settings);
        Job blocked = storage.save(new Job(details(BackgroundJobType.MOVE_OUTLOOK)));
        Job allowed = storage.save(new Job(details(BackgroundJobType.MOVE_RECEIPT)));
        String before = new JobMapper(new JacksonJsonMapper()).serializeJob(blocked);
        BackgroundJobServer server = server(gate);
        assertThat(gate.getJobsToProcess(server, new AmountRequest("createdAt:ASC", 1)))
            .extracting(Job::getId)
            .containsExactly(allowed.getId());
        assertThat(storage.getJobById(allowed.getId()).getState()).isEqualTo(StateName.PROCESSING);
        assertThat(
                new JobMapper(new JacksonJsonMapper())
                    .serializeJob(storage.getJobById(blocked.getId())))
            .isEqualTo(before);
        assertThat(storage.getJobById(blocked.getId()).getJobStatesOfType(FailedState.class))
            .isEmpty();
      }
    }

    @Test
    void actualServerDefaultChainSchedulesTenExponentialRetriesThenFails() {
      assertDefaultRetryPolicy(true);
    }

    @Test
    void unmodifiedEngineDefaultChainSchedulesTenExponentialRetriesThenFails() {
      assertDefaultRetryPolicy(false);
    }

    private void assertDefaultRetryPolicy(boolean installGate) {
      try (InMemoryStorageProvider storage = new InMemoryStorageProvider()) {
        BackgroundJobServer server =
            server(installGate ? new JobRunrIntakeStorageProvider(storage, settings) : storage);
        JobFilterUtils filters = new JobFilterUtils(server.getJobFilters());
        Job job = new Job(details(BackgroundJobType.MOVE_RECEIPT));
        long delay = 1;
        for (int failure = 1; failure <= 11; failure++) {
          if (failure > 1) {
            job.enqueue();
          }
          job.startProcessingOn(server);
          job.failed("fixture failure", new IllegalStateException("transient"));
          assertThat(job.hasStateChange()).as("state changes at failure %s", failure).isTrue();
          Instant before = Instant.now();
          filters.runOnStateElectionFilter(job);
          Instant after = Instant.now();
          assertThat(job.getJobStatesOfType(FailedState.class).count()).isEqualTo(failure);
          if (failure <= 10) {
            delay *= 3;
            assertThat(job.getState()).as("failure %s", failure).isEqualTo(StateName.SCHEDULED);
            ScheduledState scheduled = job.getJobState();
            assertThat(scheduled.getScheduledAt())
                .isBetween(before.plusSeconds(delay), after.plusSeconds(delay));
          } else {
            assertThat(job.getState()).isEqualTo(StateName.FAILED);
          }
        }
      }
    }
  }

  @Nested
  class Selection {
    @Test
    void realH2OptimisticConflictSkipsStaleClaimAndPreservesConcurrentDeletion() {
      var source = new org.h2.jdbcx.JdbcDataSource();
      source.setURL("jdbc:h2:mem:claim_conflict_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
      source.setUser("sa");
      org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
      try (var raw =
          spy(
              new org.jobrunr.storage.sql.h2.H2StorageProvider(
                  source, org.jobrunr.storage.StorageProviderUtils.DatabaseOptions.SKIP_CREATE))) {
        raw.setJobMapper(new JobMapper(new JacksonJsonMapper()));
        Job job = raw.save(new Job(details(BackgroundJobType.MOVE_RECEIPT)));
        doAnswer(
                invocation -> {
                  Object candidates = invocation.callRealMethod();
                  Job concurrent = raw.getJobById(job.getId());
                  concurrent.delete("concurrent cancellation");
                  raw.save(concurrent);
                  return candidates;
                })
            .when(raw)
            .getJobList(eq(StateName.ENQUEUED), any(AmountRequest.class));
        var gate = new JobRunrIntakeStorageProvider(raw, settings);
        assertThat(gate.getJobsToProcess(server(gate), new AmountRequest("createdAt:ASC", 1)))
            .isEmpty();
        Job retained = raw.getJobById(job.getId());
        assertThat(retained.getState()).isEqualTo(StateName.DELETED);
        assertThat(retained.getJobStatesOfType(ProcessingState.class)).isEmpty();
        assertThat(retained.getJobStatesOfType(FailedState.class)).isEmpty();
        verify(raw, never()).getJobsToProcess(any(), any());
      }
    }

    @Test
    void expandsCompletePrefixesWithoutDuplicatesAndPreservesOrderAndCutoff() {
      StorageProvider raw = mock(StorageProvider.class);
      Job blocked = new Job(details(BackgroundJobType.MOVE_OUTLOOK));
      Job allowed = new Job(details(BackgroundJobType.MOVE_RECEIPT));
      Instant cutoff = Instant.now();
      when(raw.countJobs(StateName.PROCESSING)).thenReturn(260L);
      when(raw.getJobList(eq(StateName.PROCESSING), eq(cutoff), any()))
          .thenAnswer(
              invocation -> {
                int size = invocation.<AmountRequest>getArgument(2).getLimit();
                assertThat(invocation.<AmountRequest>getArgument(2).getOrder())
                    .isEqualTo("updatedAt:ASC");
                return java.util.stream.IntStream.range(0, size)
                    .mapToObj(index -> index >= 258 ? allowed : blocked)
                    .toList();
              });
      var gate = new JobRunrIntakeStorageProvider(raw, settings);
      assertThat(
              gate.getJobList(StateName.PROCESSING, cutoff, new AmountRequest("updatedAt:ASC", 1)))
          .containsExactly(allowed);
      var requests = org.mockito.ArgumentCaptor.forClass(AmountRequest.class);
      verify(raw, times(4)).getJobList(eq(StateName.PROCESSING), eq(cutoff), requests.capture());
      assertThat(requests.getAllValues())
          .extracting(AmountRequest::getLimit)
          .containsExactly(64, 128, 256, 260);
    }

    @Test
    void scheduledSelectionStopsAtShortPrefixAndUnknownContractsRemainNative() {
      StorageProvider raw = mock(StorageProvider.class);
      Instant cutoff = Instant.now();
      Job unknown = new Job(new JobDetails(String.class.getName(), null, "toString", List.of()));
      when(raw.countJobs(StateName.SCHEDULED)).thenReturn(200L);
      when(raw.getScheduledJobs(eq(cutoff), any(AmountRequest.class))).thenReturn(List.of(unknown));
      assertThat(
              new JobRunrIntakeStorageProvider(raw, settings)
                  .getScheduledJobs(cutoff, new AmountRequest("scheduledAt:ASC", 2)))
          .containsExactly(unknown);
      verify(raw).getScheduledJobs(eq(cutoff), any(AmountRequest.class));
    }

    @Test
    void finiteCeilingTerminatesConcurrentGrowthAndFreshCallSeesNewWork() {
      StorageProvider raw = mock(StorageProvider.class);
      Job blocked = new Job(details(BackgroundJobType.MOVE_OUTLOOK));
      Job allowed = new Job(details(BackgroundJobType.MOVE_RECEIPT));
      when(raw.countJobs(StateName.ENQUEUED)).thenReturn(128L, 129L);
      when(raw.getJobList(eq(StateName.ENQUEUED), any()))
          .thenAnswer(
              invocation ->
                  java.util.stream.IntStream.range(
                          0, invocation.<AmountRequest>getArgument(1).getLimit())
                      .mapToObj(index -> index == 128 ? allowed : blocked)
                      .toList());
      var gate = new JobRunrIntakeStorageProvider(raw, settings);
      assertThat(gate.getJobList(StateName.ENQUEUED, new AmountRequest("createdAt:ASC", 1)))
          .isEmpty();
      assertThat(gate.getJobList(StateName.ENQUEUED, new AmountRequest("createdAt:ASC", 1)))
          .containsExactly(allowed);
    }

    @Test
    void handlesZeroLimitsCountsOverflowAndReadFailuresWithoutMutating() {
      StorageProvider raw = mock(StorageProvider.class);
      var gate = new JobRunrIntakeStorageProvider(raw, settings);
      assertThat(gate.getJobList(StateName.ENQUEUED, new AmountRequest("createdAt:ASC", 0)))
          .isEmpty();
      verifyNoInteractions(raw);
      assertThat(gate.getJobList(StateName.ENQUEUED, new AmountRequest("createdAt:ASC", 1)))
          .isEmpty();
      when(raw.countJobs(StateName.ENQUEUED)).thenReturn((long) Integer.MAX_VALUE + 1);
      assertThatThrownBy(
              () -> gate.getJobList(StateName.ENQUEUED, new AmountRequest("createdAt:ASC", 1)))
          .isInstanceOf(ArithmeticException.class);
      when(raw.countJobs(StateName.ENQUEUED)).thenReturn(-1L);
      assertThatThrownBy(
              () -> gate.getJobList(StateName.ENQUEUED, new AmountRequest("createdAt:ASC", 1)))
          .isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(
              () -> gate.getJobList(StateName.ENQUEUED, new AmountRequest("createdAt:ASC", -1)))
          .isInstanceOf(IllegalArgumentException.class);
      when(raw.countJobs(StateName.ENQUEUED)).thenReturn(1L);
      when(raw.getJobList(eq(StateName.ENQUEUED), any()))
          .thenThrow(new IllegalStateException("read unavailable"));
      assertThatThrownBy(
              () -> gate.getJobsToProcess(server(gate), new AmountRequest("createdAt:ASC", 1)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("read unavailable");
      verify(raw, never()).save(any(Job.class));
      verify(raw, never()).save(anyList());
    }

    @Test
    void nativeClaimSkipsOptimisticConflictsWithoutFallingBackToSqlClaims() {
      StorageProvider raw = mock(StorageProvider.class);
      Job job = new Job(details(BackgroundJobType.MOVE_RECEIPT));
      when(raw.countJobs(StateName.ENQUEUED)).thenReturn(1L);
      when(raw.getJobList(eq(StateName.ENQUEUED), any())).thenReturn(List.of(job));
      when(raw.save(anyList())).thenThrow(new ConcurrentJobModificationException(job));
      var gate = new JobRunrIntakeStorageProvider(raw, settings);
      assertThat(gate.getJobsToProcess(server(gate), new AmountRequest("createdAt:ASC", 1)))
          .isEmpty();
      verify(raw, never()).getJobsToProcess(any(), any());
    }
  }

  private BackgroundJobServer server(StorageProvider storage) {
    return new BackgroundJobServer(
        storage,
        new JacksonJsonMapper(),
        null,
        BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration()
            .andWorkerCount(1));
  }

  @Test
  void taggedProblematicExceptionIsNotRetriedByDefaultChain() {
    try (InMemoryStorageProvider storage = new InMemoryStorageProvider()) {
      BackgroundJobServer server =
          new BackgroundJobServer(
              storage,
              new JacksonJsonMapper(),
              null,
              BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration()
                  .andWorkerCount(1));
      Job job = new Job(details(BackgroundJobType.MOVE_RECEIPT));
      job.failed("invalid", JobRunrException.problematicException("unsupported callback", null));
      new JobFilterUtils(server.getJobFilters()).runOnStateElectionFilter(job);
      assertThat(job.getState()).isEqualTo(StateName.FAILED);
      assertThat(((FailedState) job.getJobState()).mustNotRetry()).isTrue();
    }
  }

  @Nested
  class Forwarding {
    @Test
    void requestedPrefixLargerThanMinimumIsNotTruncatedAndDisabledSettingsRetainUnknownContracts() {
      StorageProvider raw = mock(StorageProvider.class);
      Job known = new Job(details(BackgroundJobType.MOVE_RECEIPT));
      when(raw.countJobs(StateName.ENQUEUED)).thenReturn(200L);
      when(raw.getJobList(eq(StateName.ENQUEUED), any()))
          .thenAnswer(
              invocation -> {
                assertThat(invocation.<AmountRequest>getArgument(1).getLimit()).isEqualTo(100);
                return java.util.Collections.nCopies(100, known);
              });
      assertThat(
              new JobRunrIntakeStorageProvider(raw, settings)
                  .getJobList(StateName.ENQUEUED, new AmountRequest("createdAt:ASC", 100)))
          .hasSize(100);
      var disabled =
          DurableBackgroundJobWorker.Settings.builder()
              .enabled(false)
              .allowedTypes(Set.of(BackgroundJobType.MOVE_RECEIPT))
              .initialDelayMillis(0)
              .pollIntervalMillis(5000)
              .maxJobsPerPoll(1)
              .heartbeatTimeoutMultiplier(4)
              .build();
      doReturn(List.of(known)).when(raw).getJobList(eq(StateName.ENQUEUED), any());
      assertThat(
              new JobRunrIntakeStorageProvider(raw, disabled)
                  .getJobList(StateName.ENQUEUED, new AmountRequest("createdAt:ASC", 1)))
          .isEmpty();
    }

    @Test
    void nonCandidateOperationsKeepRawArgumentsResultsAndErrors() {
      StorageProvider raw = mock(StorageProvider.class);
      var gate = new JobRunrIntakeStorageProvider(raw, settings);
      UUID id = UUID.randomUUID();
      Job job = new Job(id, details(BackgroundJobType.MOVE_OUTLOOK));
      List<Job> list = List.of(job);
      var request = new AmountRequest("updatedAt:ASC", 7);
      var page = new org.jobrunr.storage.navigation.OffsetBasedPageRequest("createdAt:ASC", 0L, 7);
      var expectedPage = page.mapToNewPage(1, list);
      Instant cutoff = Instant.now();
      when(raw.getJobById(id)).thenReturn(job);
      assertThat(gate.getJobById(id)).isSameAs(job);
      var jobId = new org.jobrunr.jobs.JobId(id);
      when(raw.getJobById(jobId)).thenReturn(job);
      assertThat(gate.getJobById(jobId)).isSameAs(job);
      when(raw.getJobs(StateName.ENQUEUED, page)).thenReturn(expectedPage);
      assertThat(gate.getJobs(StateName.ENQUEUED, page)).isSameAs(expectedPage);
      when(raw.getScheduledJobs(cutoff, page)).thenReturn(expectedPage);
      assertThat(gate.getScheduledJobs(cutoff, page)).isSameAs(expectedPage);
      when(raw.getJobList(StateName.PROCESSING, request)).thenReturn(list);
      assertThat(gate.getJobList(StateName.PROCESSING, request)).isSameAs(list);
      when(raw.getJobList(StateName.ENQUEUED, cutoff, request)).thenReturn(list);
      assertThat(gate.getJobList(StateName.ENQUEUED, cutoff, request)).isSameAs(list);
      when(raw.getCarbonAwareJobList(cutoff, request)).thenReturn(list);
      assertThat(gate.getCarbonAwareJobList(cutoff, request)).isSameAs(list);
      when(raw.save(job)).thenReturn(job);
      when(raw.save(list)).thenReturn(list);
      assertThat(gate.save(job)).isSameAs(job);
      assertThat(gate.save(list)).isSameAs(list);
      when(raw.countJobs(StateName.PROCESSING)).thenReturn(17L);
      assertThat(gate.countJobs(StateName.PROCESSING)).isEqualTo(17);
      var stats = org.jobrunr.storage.JobStats.empty();
      when(raw.getJobStats()).thenReturn(stats);
      assertThat(gate.getJobStats()).isSameAs(stats);
      var metadata = new org.jobrunr.storage.JobRunrMetadata("fixture", "owner", "value");
      when(raw.getMetadata("fixture", "owner")).thenReturn(metadata);
      when(raw.getMetadata("fixture")).thenReturn(List.of(metadata));
      assertThat(gate.getMetadata("fixture", "owner")).isSameAs(metadata);
      assertThat(gate.getMetadata("fixture")).containsExactly(metadata);
      gate.saveMetadata(metadata);
      verify(raw).saveMetadata(metadata);
      gate.deleteMetadata("fixture");
      verify(raw).deleteMetadata("fixture");
      gate.deleteMetadata("fixture", "owner");
      verify(raw).deleteMetadata("fixture", "owner");
      var status = mock(org.jobrunr.storage.BackgroundJobServerStatus.class);
      gate.announceBackgroundJobServer(status);
      verify(raw).announceBackgroundJobServer(status);
      when(raw.signalBackgroundJobServerAlive(status)).thenReturn(true);
      assertThat(gate.signalBackgroundJobServerAlive(status)).isTrue();
      gate.signalBackgroundJobServerStopped(status);
      verify(raw).signalBackgroundJobServerStopped(status);
      when(raw.getBackgroundJobServers()).thenReturn(List.of(status));
      assertThat(gate.getBackgroundJobServers()).containsExactly(status);
      when(raw.getLongestRunningBackgroundJobServerId()).thenReturn(id);
      assertThat(gate.getLongestRunningBackgroundJobServerId()).isEqualTo(id);
      when(raw.removeTimedOutBackgroundJobServers(cutoff)).thenReturn(2);
      assertThat(gate.removeTimedOutBackgroundJobServers(cutoff)).isEqualTo(2);
      var recurring = mock(org.jobrunr.jobs.RecurringJob.class);
      var recurringResult = mock(org.jobrunr.storage.RecurringJobsResult.class);
      when(raw.saveRecurringJob(recurring)).thenReturn(recurring);
      when(raw.getRecurringJobs()).thenReturn(recurringResult);
      when(raw.recurringJobsUpdated(7L)).thenReturn(true);
      when(raw.deleteRecurringJob("recurring")).thenReturn(1);
      assertThat(gate.saveRecurringJob(recurring)).isSameAs(recurring);
      assertThat(gate.getRecurringJobs()).isSameAs(recurringResult);
      assertThat(gate.recurringJobsUpdated(7L)).isTrue();
      assertThat(gate.deleteRecurringJob("recurring")).isEqualTo(1);
      when(raw.getDistinctJobSignatures(StateName.ENQUEUED)).thenReturn(Set.of("signature"));
      assertThat(gate.getDistinctJobSignatures(StateName.ENQUEUED)).containsExactly("signature");
      when(raw.getRecurringJobLatestScheduledInstant("recurring", StateName.SCHEDULED))
          .thenReturn(cutoff);
      assertThat(gate.getRecurringJobLatestScheduledInstant("recurring", StateName.SCHEDULED))
          .isEqualTo(cutoff);
      when(raw.deletePermanently(id)).thenReturn(1);
      when(raw.deleteJobsPermanently(StateName.DELETED, cutoff)).thenReturn(3);
      assertThat(gate.deletePermanently(id)).isEqualTo(1);
      assertThat(gate.deleteJobsPermanently(StateName.DELETED, cutoff)).isEqualTo(3);
      var mapper = new JobMapper(new JacksonJsonMapper());
      gate.setJobMapper(mapper);
      verify(raw).setJobMapper(mapper);
      gate.setUpStorageProvider(
          org.jobrunr.storage.StorageProviderUtils.DatabaseOptions.SKIP_CREATE);
      verify(raw)
          .setUpStorageProvider(
              org.jobrunr.storage.StorageProviderUtils.DatabaseOptions.SKIP_CREATE);
      var info = mock(StorageProvider.StorageProviderInfo.class);
      when(raw.getStorageProviderInfo()).thenReturn(info);
      assertThat(gate.getStorageProviderInfo()).isSameAs(info);
      var listener = mock(org.jobrunr.storage.listeners.StorageProviderChangeListener.class);
      gate.addJobStorageOnChangeListener(listener);
      verify(raw).addJobStorageOnChangeListener(listener);
      gate.removeJobStorageOnChangeListener(listener);
      verify(raw).removeJobStorageOnChangeListener(listener);
      gate.publishTotalAmountOfSucceededJobs(3);
      verify(raw).publishTotalAmountOfSucceededJobs(3);
      gate.validatePollInterval(java.time.Duration.ofSeconds(5));
      verify(raw).validatePollInterval(java.time.Duration.ofSeconds(5));
      gate.validateRecurringJobInterval(java.time.Duration.ofMinutes(1));
      verify(raw).validateRecurringJobInterval(java.time.Duration.ofMinutes(1));
      gate.close();
      verify(raw).close();
      IllegalStateException unavailable = new IllegalStateException("raw failure");
      when(raw.getJobById(id)).thenThrow(unavailable);
      assertThatThrownBy(() -> gate.getJobById(id)).isSameAs(unavailable);
    }
  }

  private JobDetails details(BackgroundJobType type) {
    return new JobDetails(
        DurableBackgroundJobWorker.class.getName(),
        null,
        "executeV1",
        List.of(new JobParameter(String.class, type.name()), JobParameter.JobContext));
  }
}
