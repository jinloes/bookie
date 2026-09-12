package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.domain.BackgroundJobType;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class JobRunrIntakeConfigurationTest {
  private final JobRunrIntakeConfiguration configuration = new JobRunrIntakeConfiguration();

  @Nested
  class Settings {
    @Test
    void defaultsEnableReceiptMovesAndDeriveHeartbeatTimeout() {
      var settings = configuration.intakeWorkerSettings(new MockEnvironment());
      assertThat(settings.enabled()).isTrue();
      assertThat(settings.allowedTypes())
          .contains(BackgroundJobType.MOVE_RECEIPT)
          .doesNotContain(BackgroundJobType.MOVE_OUTLOOK);
      assertThat(settings.heartbeatTimeoutMultiplier()).isEqualTo(60);
      assertThat(settings.initialDelayMillis()).isEqualTo(5000);
    }

    @ParameterizedTest
    @CsvSource({"1,4", "20,4", "21,5", "300,60"})
    void deprecatedLeaseAliasRoundsUp(long seconds, int multiplier) {
      assertThat(
              configuration
                  .intakeWorkerSettings(
                      new MockEnvironment()
                          .withProperty(
                              "bookie.intake.worker.lease-seconds", Long.toString(seconds)))
                  .heartbeatTimeoutMultiplier())
          .isEqualTo(multiplier);
    }

    @ParameterizedTest
    @CsvSource({
      "jobrunr.background-job-server.enabled,false",
      "jobrunr.jobs.default-number-of-retries,10",
      "jobrunr.jobs.retry-back-off-time-seed,3",
      "jobrunr.dashboard.enabled,true",
      "jobrunr.miscellaneous.allow-anonymous-data-usage,true",
      "jobrunr.database.skip-create,false",
      "jobrunr.background-job-server.worker-count,2",
      "jobrunr.background-job-server.poll-interval-in-seconds,1",
      "bookie.intake.worker.lease-seconds,0",
      "bookie.intake.worker.max-jobs-per-poll,0",
      "bookie.intake.worker.initial-delay-ms,-1",
      "bookie.intake.worker.poll-interval-ms,0",
      "jobrunr.database.type,nosql",
      "jobrunr.database.table-prefix,other",
      "jobrunr.database.datasource,other",
      "jobrunr.job-scheduler.enabled,false",
      "jobrunr.background-job-server.server-timeout-poll-interval-multiplicand,4"
    })
    void rejectsContradictionsBeforeEngineConstruction(String key, String value) {
      assertThatThrownBy(
              () ->
                  configuration.intakeWorkerSettings(
                      new MockEnvironment().withProperty(key, value)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explicitEmptyAllowlistStaysEmpty() {
      assertThat(
              configuration
                  .intakeWorkerSettings(
                      new MockEnvironment()
                          .withProperty("bookie.intake.worker.allowed-job-types", ""))
                  .allowedTypes())
          .isEmpty();
    }
  }

  @Nested
  class Lifecycle {
    @Test
    void readyStartsOneDecoratedServerWithUnmodifiedDefaultRetryChainAndStopsBeforeRawClose() {
      var settings = configuration.intakeWorkerSettings(new MockEnvironment());
      try (var raw = spy(new org.jobrunr.storage.InMemoryStorageProvider())) {
        raw.setJobMapper(new org.jobrunr.jobs.mappers.JobMapper(new JacksonJsonMapper()));
        DurableBackgroundJobWorker worker = mock(DurableBackgroundJobWorker.class);
        var lifecycle =
            configuration.intakeEngineLifecycle(
                raw, new JacksonJsonMapper(), mock(ApplicationContext.class), worker, settings);
        assertThat(raw.getBackgroundJobServers()).isEmpty();
        try {
          lifecycle.ready();
          lifecycle.ready();
          var server =
              (org.jobrunr.server.BackgroundJobServer)
                  org.springframework.test.util.ReflectionTestUtils.getField(lifecycle, "server");
          assertThat(server).isNotNull();
          assertThat(server.isRunning()).isTrue();
          assertThat(server.getStorageProvider()).isNotSameAs(raw);
          assertThat(server.getConfiguration().getPollInterval())
              .isEqualTo(java.time.Duration.ofSeconds(5));
          verify(worker).ready();
          var job =
              new org.jobrunr.jobs.Job(
                  new org.jobrunr.jobs.JobDetails(
                      DurableBackgroundJobWorker.class.getName(),
                      null,
                      "executeV1",
                      java.util.List.of(
                          new org.jobrunr.jobs.JobParameter(String.class, "MOVE_RECEIPT"),
                          org.jobrunr.jobs.JobParameter.JobContext)));
          var filters = new org.jobrunr.jobs.filters.JobFilterUtils(server.getJobFilters());
          long delay = 1;
          for (int failure = 1; failure <= 11; failure++) {
            if (failure > 1) {
              job.enqueue();
            }
            job.startProcessingOn(server);
            job.failed("fixture", new IllegalStateException("transient"));
            java.time.Instant before = java.time.Instant.now();
            filters.runOnStateElectionFilter(job);
            java.time.Instant after = java.time.Instant.now();
            assertThat(job.getAmountOfRetries()).isNull();
            assertThat(
                    job.getLastJobStateOfType(org.jobrunr.jobs.states.FailedState.class)
                        .orElseThrow()
                        .mustNotRetry())
                .isFalse();
            if (failure <= 10) {
              delay *= 3;
              assertThat(job.getState()).isEqualTo(org.jobrunr.jobs.states.StateName.SCHEDULED);
              org.jobrunr.jobs.states.ScheduledState scheduled = job.getJobState();
              assertThat(scheduled.getScheduledAt())
                  .isBetween(before.plusSeconds(delay), after.plusSeconds(delay));
            } else {
              assertThat(job.getState()).isEqualTo(org.jobrunr.jobs.states.StateName.FAILED);
            }
          }
        } finally {
          lifecycle.destroy();
        }
        verify(worker).stop();
        verify(raw, never()).close();
        var server =
            (org.jobrunr.server.BackgroundJobServer)
                org.springframework.test.util.ReflectionTestUtils.getField(lifecycle, "server");
        assertThat(server.isRunning()).isFalse();
      }
    }

    @Test
    void disabledWorkerNeverConstructsServerEvenOnReady() {
      var settings =
          configuration.intakeWorkerSettings(
              new MockEnvironment().withProperty("bookie.intake.worker.enabled", "false"));
      StorageProvider storage = mock(StorageProvider.class);
      DurableBackgroundJobWorker worker = mock(DurableBackgroundJobWorker.class);
      var lifecycle =
          configuration.intakeEngineLifecycle(
              storage, new JacksonJsonMapper(), mock(ApplicationContext.class), worker, settings);
      verifyNoInteractions(storage, worker);
      lifecycle.ready();
      verifyNoInteractions(storage, worker);
      lifecycle.destroy();
      verify(worker).stop();
      verifyNoInteractions(storage);
    }
  }
}
