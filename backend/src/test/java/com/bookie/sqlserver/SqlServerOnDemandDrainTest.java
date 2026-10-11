package com.bookie.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.IntakeJobDispatcher;
import com.bookie.intake.application.JobExecutionResult;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.infrastructure.JobRunrIntakeConfiguration;
import com.bookie.intake.infrastructure.OnDemandIntakeDrainer;
import com.bookie.util.WriteFreezeState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.JobParameter;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/** On demand, the intake engine stops once idle so the database can pause, and restarts. */
@Tag("sqlserver")
class SqlServerOnDemandDrainTest {

  private static final Duration PAST_QUIET_PERIOD = Duration.ofSeconds(61);

  @Nested
  class RequestDrain {

    @Test
    void stopsTheEngineWhenIdleAndRunsTheNextJobOnlyAfterAnotherDrain() throws Exception {
      SqlServerTestContainer.Database database = SqlServerTestContainer.createDatabase();
      MutableClock clock = new MutableClock(Instant.parse("2026-09-01T12:00:00Z"));
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.execute(any())).thenReturn(JobExecutionResult.completed());

      try (var context = context(database.appDataSource(), clock, dispatcher)) {
        StorageProvider storage = context.getBean(StorageProvider.class);
        assertThat(storage.getBackgroundJobServers()).isEmpty();

        context.publishEvent(
            new ApplicationReadyEvent(
                new SpringApplication(), new String[0], context, Duration.ZERO));
        await()
            .atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> assertThat(storage.getBackgroundJobServers()).isNotEmpty());
        Job first = storage.save(new Job(details()));
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(
                () ->
                    assertThat(storage.getJobById(first.getId()).getState())
                        .isEqualTo(StateName.SUCCEEDED));

        clock.advance(PAST_QUIET_PERIOD);
        await()
            .atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> assertThat(storage.getBackgroundJobServers()).isEmpty());

        Job second = storage.save(new Job(details()));
        await()
            .during(Duration.ofSeconds(7))
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(
                () ->
                    assertThat(storage.getJobById(second.getId()).getState())
                        .isEqualTo(StateName.ENQUEUED));
        verify(dispatcher, times(1)).execute(any());

        context.getBean(OnDemandIntakeDrainer.class).requestDrain();
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(
                () ->
                    assertThat(storage.getJobById(second.getId()).getState())
                        .isEqualTo(StateName.SUCCEEDED));
        verify(dispatcher, times(2)).execute(any());
      }
    }
  }

  private static AnnotationConfigApplicationContext context(
      DataSource source, Clock clock, IntakeJobDispatcher dispatcher) {
    var context = new AnnotationConfigApplicationContext();
    context.setEnvironment(
        new MockEnvironment()
            .withProperty("bookie.db.vendor", "sqlserver")
            .withProperty("bookie.intake.execution-mode", "ON_DEMAND")
            .withProperty("jobrunr.database.skip-create", "false")
            .withProperty("bookie.intake.worker.allowed-job-types", "MOVE_RECEIPT")
            .withProperty("bookie.intake.worker.initial-delay-ms", "0")
            .withProperty("bookie.intake.worker.poll-interval-ms", "200")
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
    WriteFreezeState notFrozen = () -> false;
    context.registerBean(BackgroundJobService.class, () -> service);
    context.registerBean(WriteFreezeState.class, () -> notFrozen);
    context.registerBean(Clock.class, () -> clock);
    context.registerBean(
        DurableBackgroundJobWorker.class,
        () ->
            new DurableBackgroundJobWorker(
                service,
                dispatcher,
                context.getBean(JobScheduler.class),
                context.getBean(StorageProvider.class),
                context.getBean(DurableBackgroundJobWorker.Settings.class),
                clock,
                notFrozen));
    context.register(JobRunrIntakeConfiguration.class);
    context.refresh();
    return context;
  }

  private static JobDetails details() {
    return new JobDetails(
        DurableBackgroundJobWorker.class.getName(),
        null,
        "executeV1",
        List.of(
            new JobParameter(String.class, BackgroundJobType.MOVE_RECEIPT.name()),
            JobParameter.JobContext));
  }

  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> now;

    MutableClock(Instant start) {
      now = new AtomicReference<>(start);
    }

    void advance(Duration duration) {
      now.updateAndGet(instant -> instant.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }
}
