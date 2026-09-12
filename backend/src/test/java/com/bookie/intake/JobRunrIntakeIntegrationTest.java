package com.bookie.intake;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookie.intake.application.*;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.infrastructure.JobRunrIntakeConfiguration;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.jobrunr.jobs.*;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.jobs.states.*;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.StorageProviderUtils.DatabaseOptions;
import org.jobrunr.storage.sql.h2.H2StorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class JobRunrIntakeIntegrationTest {
  @TempDir Path directory;

  @Nested
  class NativeExecution {
    @Test
    void unsupportedPersistedCallbackFailsNativelyWithoutRetryOrProviders() {
      JdbcDataSource source =
          source("jdbc:h2:mem:invalid_callback_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
      migrate(source);
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      try (var context = context(source, "", dispatcher)) {
        StorageProvider raw = context.getBean(StorageProvider.class);
        Job job =
            raw.save(
                new Job(
                    new JobDetails(
                        DurableBackgroundJobWorker.class.getName(),
                        null,
                        "executeV1",
                        List.of(
                            new JobParameter(String.class, "UNKNOWN"), JobParameter.JobContext))));
        ready(context);
        await()
            .atMost(Duration.ofSeconds(20))
            .untilAsserted(
                () ->
                    assertThat(raw.getJobById(job.getId()).getState()).isEqualTo(StateName.FAILED));
        String failed = json(raw.getJobById(job.getId()));
        assertThat(
                raw.getJobById(job.getId())
                    .getLastJobStateOfType(FailedState.class)
                    .orElseThrow()
                    .mustNotRetry())
            .isTrue();
        await()
            .during(Duration.ofSeconds(12))
            .atMost(Duration.ofSeconds(16))
            .untilAsserted(() -> assertThat(json(raw.getJobById(job.getId()))).isEqualTo(failed));
        verifyNoInteractions(dispatcher);
      }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realPerformerRetriesTransientProviderFailureThenPromotesAndSucceeds(
        boolean infrastructure) {
      JdbcDataSource source =
          source("jdbc:h2:mem:performer_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
      migrate(source);
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.execute(any()))
          .thenThrow(
              infrastructure
                  ? new org.springframework.dao.DataAccessResourceFailureException(
                      "database unavailable")
                  : JobExecutionException.retryable("transient fixture", null))
          .thenReturn(JobExecutionResult.completed());
      try (var context = context(source, "MOVE_RECEIPT", dispatcher)) {
        StorageProvider raw = context.getBean(StorageProvider.class);
        Job job = raw.save(new Job(details(BackgroundJobType.MOVE_RECEIPT)));
        assertThat(raw.getBackgroundJobServers()).isEmpty();
        ready(context);
        await()
            .atMost(Duration.ofSeconds(25))
            .untilAsserted(
                () ->
                    assertThat(raw.getJobById(job.getId()).getState())
                        .isEqualTo(StateName.SCHEDULED));
        Job scheduled = raw.getJobById(job.getId());
        FailedState failed = scheduled.getLastJobStateOfType(FailedState.class).orElseThrow();
        ScheduledState retry = scheduled.getJobState();
        assertThat(Duration.between(failed.getCreatedAt(), retry.getScheduledAt()).toMillis())
            .isBetween(3000L, 4000L);
        await()
            .atMost(Duration.ofSeconds(25))
            .untilAsserted(
                () ->
                    assertThat(raw.getJobById(job.getId()).getState())
                        .isEqualTo(StateName.SUCCEEDED));
        verify(dispatcher, times(2)).execute(any());
        assertThat(raw.getJobById(job.getId()).getJobStatesOfType(FailedState.class)).hasSize(1);
      }
    }

    @Test
    void tiedDisabledPrefixesRetainAllThreeStatesAcrossRestartsWhileAllowedWorkProceeds() {
      JdbcDataSource source = source("jdbc:h2:file:" + directory.resolve("gate"));
      migrate(source);
      Map<UUID, String> disabled = new LinkedHashMap<>();
      List<UUID> allowed = new ArrayList<>();
      Instant old = Instant.now().minusSeconds(120);
      try (H2StorageProvider raw = storage(source)) {
        for (int i = 0; i < 130; i++) {
          for (StateName state :
              List.of(StateName.ENQUEUED, StateName.SCHEDULED, StateName.PROCESSING)) {
            Job job = raw.save(candidate(BackgroundJobType.MOVE_OUTLOOK, state, old));
            disabled.put(job.getId(), json(job));
          }
        }
        for (StateName state :
            List.of(StateName.ENQUEUED, StateName.SCHEDULED, StateName.PROCESSING)) {
          allowed.add(raw.save(candidate(BackgroundJobType.MOVE_RECEIPT, state, old)).getId());
        }
      }
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.execute(any())).thenReturn(JobExecutionResult.completed());
      try (var context = context(source, "MOVE_RECEIPT", dispatcher)) {
        ready(context);
        StorageProvider raw = context.getBean(StorageProvider.class);
        await()
            .atMost(Duration.ofSeconds(45))
            .untilAsserted(
                () ->
                    assertThat(allowed)
                        .allSatisfy(
                            id ->
                                assertThat(raw.getJobById(id).getState())
                                    .isEqualTo(StateName.SUCCEEDED)));
        assertUnchanged(raw, disabled);
        assertThat(raw.getJobStats().getProcessing()).isEqualTo(130);
        assertThat(raw.getJobStats().getScheduled()).isEqualTo(130);
        assertThat(raw.getJobStats().getEnqueued()).isEqualTo(130);
        verify(dispatcher, times(3)).execute(any());
      }
      try (var context = context(source, "", dispatcher)) {
        ready(context);
        StorageProvider raw = context.getBean(StorageProvider.class);
        await()
            .during(Duration.ofSeconds(12))
            .atMost(Duration.ofSeconds(16))
            .untilAsserted(() -> assertUnchanged(raw, disabled));
        verify(dispatcher, times(3)).execute(any());
      }
      // Re-enable the same persisted UUIDs; no rebinding, re-enqueueing or rewritten history.
      try (var context = context(source, "MOVE_OUTLOOK", dispatcher)) {
        ready(context);
        StorageProvider raw = context.getBean(StorageProvider.class);
        await()
            .atMost(Duration.ofSeconds(90))
            .untilAsserted(
                () ->
                    assertThat(
                            raw.getJobList(
                                StateName.SUCCEEDED,
                                new org.jobrunr.storage.navigation.AmountRequest(
                                    "createdAt:ASC", 1000)))
                        .extracting(Job::getId)
                        .containsAll(disabled.keySet()));
        Map<UUID, Job> completed =
            raw
                .getJobList(
                    StateName.SUCCEEDED,
                    new org.jobrunr.storage.navigation.AmountRequest("createdAt:ASC", 1000))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Job::getId, job -> job));
        for (UUID id : disabled.keySet()) {
          Job original = new JobMapper(new JacksonJsonMapper()).deserializeJob(disabled.get(id));
          Job resumed = completed.get(id);
          assertThat(resumed.getId()).isEqualTo(original.getId());
          assertThat(resumed.getJobStatesOfType(FailedState.class).count())
              .isEqualTo(original.getState() == StateName.PROCESSING ? 1 : 0);
          assertThat(resumed.getJobStates().getFirst().getCreatedAt())
              .isEqualTo(original.getJobStates().getFirst().getCreatedAt());
        }
      }
    }

    @Test
    void healthyLongProviderExecutionKeepsNativeHeartbeatsWithoutOrphanFailures() throws Exception {
      JdbcDataSource source =
          source("jdbc:h2:mem:heartbeat_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
      migrate(source);
      CountDownLatch release = new CountDownLatch(1);
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.execute(any()))
          .thenAnswer(
              invocation -> {
                if (!release.await(50, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("Fixture release timed out");
                }
                return JobExecutionResult.completed();
              });
      try (var context = context(source, "MOVE_RECEIPT", dispatcher)) {
        StorageProvider raw = context.getBean(StorageProvider.class);
        Job job = raw.save(new Job(details(BackgroundJobType.MOVE_RECEIPT)));
        ready(context);
        try {
          await()
              .atMost(Duration.ofSeconds(20))
              .untilAsserted(
                  () ->
                      assertThat(raw.getJobById(job.getId()).getState())
                          .isEqualTo(StateName.PROCESSING));
          Instant started = raw.getJobById(job.getId()).getUpdatedAt();
          await()
              .pollDelay(Duration.ofSeconds(25))
              .atMost(Duration.ofSeconds(35))
              .untilAsserted(
                  () -> {
                    Job running = raw.getJobById(job.getId());
                    assertThat(running.getState()).isEqualTo(StateName.PROCESSING);
                    assertThat(running.getUpdatedAt()).isAfter(started.plusSeconds(15));
                    assertThat(running.getJobStatesOfType(FailedState.class)).isEmpty();
                  });
        } finally {
          release.countDown();
        }
        await()
            .atMost(Duration.ofSeconds(15))
            .untilAsserted(
                () ->
                    assertThat(raw.getJobById(job.getId()).getState())
                        .isEqualTo(StateName.SUCCEEDED));
      } finally {
        release.countDown();
      }
    }
  }

  @Nested
  class CrashRecovery {
    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    void abruptChildExitRetainsDisabledOrphanThenUsesRemainingNativeRetries(int failures)
        throws Exception {
      String url = "jdbc:h2:file:" + directory.resolve("crash");
      JdbcDataSource source = source(url);
      migrate(source);
      UUID id;
      try (H2StorageProvider raw = storage(source)) {
        Job job = new Job(details(BackgroundJobType.MOVE_RECEIPT));
        for (int i = 0; i < failures; i++) {
          job.failed("historical fixture", new IllegalStateException("transient"));
          job.enqueue();
        }
        id = raw.save(job).getId();
      }
      Path signal = directory.resolve("provider-entered");
      Path log = directory.resolve("child.log");
      Process child =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "-Duser.home=" + directory,
                  "-cp",
                  childClasspath(),
                  CrashChild.class.getName(),
                  url,
                  signal.toString())
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .start();
      try {
        await()
            .atMost(Duration.ofSeconds(35))
            .until(() -> Files.exists(signal) || !child.isAlive());
        assertThat(child.isAlive())
            .withFailMessage(
                () -> {
                  try {
                    return Files.readString(log);
                  } catch (java.io.IOException e) {
                    return e.toString();
                  }
                })
            .isTrue();
        assertThat(signal).isRegularFile();
        child.destroyForcibly();
        assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        if (child.isAlive()) {
          child.destroyForcibly();
          child.waitFor(10, TimeUnit.SECONDS);
        }
      }
      String orphan;
      try (H2StorageProvider raw = storage(source)) {
        Job job = raw.getJobById(id);
        assertThat(job.getState()).isEqualTo(StateName.PROCESSING);
        assertThat(job.getJobStatesOfType(FailedState.class)).hasSize(failures);
        orphan = json(job);
      }
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.execute(any())).thenReturn(JobExecutionResult.completed());
      try (var context = context(source, "", dispatcher)) {
        ready(context);
        StorageProvider raw = context.getBean(StorageProvider.class);
        await()
            .during(Duration.ofSeconds(25))
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> assertThat(json(raw.getJobById(id))).isEqualTo(orphan));
        verifyNoInteractions(dispatcher);
      }
      try (var context = context(source, "MOVE_RECEIPT", dispatcher)) {
        ready(context);
        StorageProvider raw = context.getBean(StorageProvider.class);
        await()
            .atMost(Duration.ofSeconds(40))
            .untilAsserted(
                () -> {
                  Job recovered = raw.getJobById(id);
                  assertThat(recovered.getState())
                      .isEqualTo(failures == 10 ? StateName.FAILED : StateName.SUCCEEDED);
                  assertThat(recovered.getJobStatesOfType(FailedState.class)).hasSize(failures + 1);
                });
        if (failures == 10) {
          verifyNoInteractions(dispatcher);
        } else {
          verify(dispatcher).execute(any());
        }
      }
    }
  }

  @Nested
  class RestoreOrdering {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualStartupValidatesRestoreBeforeReadyAndCorruptionPreventsProviders(boolean corrupt)
        throws Exception {
      directory = directory.toRealPath();
      JdbcDataSource source =
          source("jdbc:h2:mem:restore_order_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
      migrate(source);
      UUID id;
      try (var raw = storage(source)) {
        id = raw.save(new Job(details(BackgroundJobType.MOVE_RECEIPT))).getId();
      }
      var verifier = new com.bookie.datalifecycle.migration.MigrationIntegrityVerifier();
      var manifest = verifier.capture(source, "test", "fixture");
      Path manifestFile = directory.resolve("manifest.json");
      new com.bookie.datalifecycle.migration.MigrationIntegrityManifestCodec()
          .write(manifestFile, manifest);
      var journals =
          new com.bookie.datalifecycle.restore.RestoreJournalStore(
              new com.bookie.datalifecycle.restore.RestorePaths(directory));
      String now = Instant.now().toString();
      journals.write(
          new com.bookie.datalifecycle.restore.RestoreJournal(
              com.bookie.datalifecycle.restore.RestoreJournal.CURRENT_VERSION,
              "fixture",
              com.bookie.datalifecycle.restore.RestoreState.SHADOW_ACTIVATED,
              "file",
              "backup.sql",
              1,
              "source",
              directory.toString(),
              directory.resolve("live.mv.db").toString(),
              directory.resolve("shadow.mv.db").toString(),
              directory.resolve("rollback.mv.db").toString(),
              directory.resolve("failed.mv.db").toString(),
              manifestFile.toString(),
              directory.resolve("audit.json").toString(),
              "live",
              "fixture",
              manifest.contentChecksum(),
              now,
              now,
              "activated",
              ""));
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.execute(any())).thenReturn(JobExecutionResult.completed());
      SpringApplication application = new SpringApplication(JobRunrIntakeConfiguration.class);
      application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
      application.setRegisterShutdownHook(false);
      application.setApplicationContextFactory(
          type -> {
            var context = unrefreshedContext(source, "MOVE_RECEIPT", dispatcher);
            context.removeBeanDefinition("jobRunrIntakeConfiguration");
            context.registerBean(
                com.bookie.datalifecycle.restore.PostRestoreValidator.class,
                () ->
                    new com.bookie.datalifecycle.restore.PostRestoreValidator(
                        source, verifier, journals));
            return context;
          });
      if (corrupt) {
        new org.springframework.jdbc.core.JdbcTemplate(source)
            .update("UPDATE jobrunr_metadata SET `value` = '999'");
        assertThatThrownBy(
                () ->
                    application.run(
                        "--spring.config.location=optional:classpath:/no-fixture-config.properties",
                        "--bookie.intake.worker.allowed-job-types=MOVE_RECEIPT",
                        "--bookie.intake.worker.lease-seconds=20"))
            .hasStackTraceContaining("rollback scheduled");
        verifyNoInteractions(dispatcher);
        assertThat(journals.read().orElseThrow().state())
            .isEqualTo(com.bookie.datalifecycle.restore.RestoreState.ROLLBACK_REQUIRED);
        try (var raw = storage(source)) {
          assertThat(raw.getJobById(id).getState()).isEqualTo(StateName.ENQUEUED);
          assertThat(raw.getBackgroundJobServers()).isEmpty();
        }
      } else {
        try (var context =
            application.run(
                "--spring.config.location=optional:classpath:/no-fixture-config.properties",
                "--bookie.intake.worker.allowed-job-types=MOVE_RECEIPT",
                "--bookie.intake.worker.lease-seconds=20")) {
          assertThat(journals.read().orElseThrow().state())
              .isEqualTo(com.bookie.datalifecycle.restore.RestoreState.POST_START_VALIDATED);
          StorageProvider raw = context.getBean(StorageProvider.class);
          await()
              .atMost(Duration.ofSeconds(25))
              .untilAsserted(
                  () -> assertThat(raw.getJobById(id).getState()).isEqualTo(StateName.SUCCEEDED));
          verify(dispatcher).execute(any());
        }
      }
    }
  }

  public static class CrashChild {
    public static void main(String[] args) throws Exception {
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.execute(any()))
          .thenAnswer(
              invocation -> {
                Files.writeString(Path.of(args[1]), "PROCESSING");
                new CountDownLatch(1).await();
                return JobExecutionResult.completed();
              });
      try (var context = context(source(args[0]), "MOVE_RECEIPT", dispatcher)) {
        ready(context);
        new CountDownLatch(1).await();
      }
    }
  }

  static AnnotationConfigApplicationContext context(
      DataSource source, String allowed, IntakeJobDispatcher dispatcher) {
    var context = unrefreshedContext(source, allowed, dispatcher);
    context.refresh();
    return context;
  }

  static AnnotationConfigApplicationContext unrefreshedContext(
      DataSource source, String allowed, IntakeJobDispatcher dispatcher) {
    var context = new AnnotationConfigApplicationContext();
    context.setEnvironment(
        new MockEnvironment()
            .withProperty("bookie.intake.worker.allowed-job-types", allowed)
            .withProperty("bookie.intake.worker.initial-delay-ms", "0")
            .withProperty("bookie.intake.worker.lease-seconds", "20"));
    context.registerBean(DataSource.class, () -> source);
    BackgroundJobService service = mock(BackgroundJobService.class);
    when(service.start(any(), any(), anyInt()))
        .thenAnswer(
            invocation ->
                List.of(
                    BackgroundJob.builder()
                        .id(1L)
                        .executionId(invocation.getArgument(0))
                        .type(invocation.getArgument(1))
                        .build()));
    when(service.finish(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              JobExecutionOutcome outcome = invocation.getArgument(2);
              return !outcome.successful()
                  && outcome.failure() instanceof JobExecutionException failure
                  && failure.getKind() == JobExecutionException.FailureKind.RETRYABLE;
            });
    context.registerBean(
        DurableBackgroundJobWorker.class,
        () ->
            new DurableBackgroundJobWorker(
                service,
                dispatcher,
                context.getBean(JobScheduler.class),
                context.getBean(StorageProvider.class),
                context.getBean(DurableBackgroundJobWorker.Settings.class),
                Clock.systemUTC()));
    context.register(JobRunrIntakeConfiguration.class);
    return context;
  }

  static void ready(AnnotationConfigApplicationContext context) {
    context.publishEvent(
        new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
  }

  static JdbcDataSource source(String url) {
    JdbcDataSource source = new JdbcDataSource();
    source.setURL(url);
    source.setUser("sa");
    return source;
  }

  static void migrate(DataSource source) {
    Flyway.configure().dataSource(source).load().migrate();
  }

  static H2StorageProvider storage(DataSource source) {
    H2StorageProvider storage = new H2StorageProvider(source, DatabaseOptions.SKIP_CREATE);
    storage.setJobMapper(new JobMapper(new JacksonJsonMapper()));
    return storage;
  }

  static JobDetails details(BackgroundJobType type) {
    return new JobDetails(
        DurableBackgroundJobWorker.class.getName(),
        null,
        "executeV1",
        List.of(new JobParameter(String.class, type.name()), JobParameter.JobContext));
  }

  static Job candidate(BackgroundJobType type, StateName state, Instant old) {
    return new Job(
        details(type),
        switch (state) {
          case ENQUEUED -> new EnqueuedState(old);
          case SCHEDULED -> new ScheduledState(old, "fixture");
          case PROCESSING -> new ProcessingState(UUID.randomUUID(), "dead-fixture", old, old);
          default -> throw new IllegalArgumentException("Not a candidate state: " + state);
        });
  }

  static String json(Job job) {
    return new JobMapper(new JacksonJsonMapper()).serializeJob(job);
  }

  static void assertUnchanged(StorageProvider raw, Map<UUID, String> jobs) {
    Map<UUID, String> actual = new HashMap<>();
    for (StateName state : List.of(StateName.ENQUEUED, StateName.SCHEDULED, StateName.PROCESSING)) {
      raw.getJobList(state, new org.jobrunr.storage.navigation.AmountRequest("createdAt:ASC", 1000))
          .forEach(job -> actual.put(job.getId(), json(job)));
    }
    assertThat(actual).containsAllEntriesOf(jobs);
  }

  static String childClasspath() throws Exception {
    Set<String> entries =
        new LinkedHashSet<>(
            List.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
    for (ClassLoader loader = CrashChild.class.getClassLoader();
        loader != null;
        loader = loader.getParent()) {
      if (loader instanceof URLClassLoader urls) {
        for (var url : urls.getURLs()) {
          entries.add(Path.of(url.toURI()).toString());
        }
      }
    }
    return String.join(java.io.File.pathSeparator, entries);
  }
}
