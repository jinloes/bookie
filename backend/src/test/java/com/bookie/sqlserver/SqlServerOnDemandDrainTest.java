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
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.infrastructure.IntakeWorkerConfiguration;
import com.bookie.intake.infrastructure.OnDemandIntakeDrainer;
import com.bookie.util.WriteFreezeState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/** On demand, the intake drainer stops once idle so the database can pause, and restarts. */
@Tag("sqlserver")
class SqlServerOnDemandDrainTest {

  private static final Duration PAST_QUIET_PERIOD = Duration.ofSeconds(61);

  @Nested
  class RequestDrain {

    @Test
    void stopsDrainingWhenIdleAndRunsTheNextJobOnlyAfterAnotherDrain() throws Exception {
      SqlServerTestContainer.Database database = SqlServerTestContainer.createDatabase();
      MutableClock clock = new MutableClock(Instant.parse("2026-09-01T12:00:00Z"));
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.execute(any())).thenReturn(JobExecutionResult.completed());
      ConcurrentLinkedQueue<BackgroundJob> queue = new ConcurrentLinkedQueue<>();

      try (var context = context(database.appDataSource(), clock, dispatcher, queue)) {
        OnDemandIntakeDrainer drainer = context.getBean(OnDemandIntakeDrainer.class);
        assertThat(drainer.isDraining()).isFalse();

        queue.add(job(1L));
        context.publishEvent(
            new ApplicationReadyEvent(
                new SpringApplication(), new String[0], context, Duration.ZERO));
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> verify(dispatcher, times(1)).execute(any()));

        clock.advance(PAST_QUIET_PERIOD);
        await().atMost(Duration.ofSeconds(10)).until(() -> !drainer.isDraining());

        queue.add(job(2L));
        await()
            .during(Duration.ofSeconds(2))
            .atMost(Duration.ofSeconds(4))
            .untilAsserted(() -> verify(dispatcher, times(1)).execute(any()));

        drainer.requestDrain();
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> verify(dispatcher, times(2)).execute(any()));
      }
    }
  }

  private static BackgroundJob job(long id) {
    return BackgroundJob.builder()
        .id(id)
        .state(BackgroundJobState.LEASED)
        .leaseOwner("lease-" + id)
        .type(BackgroundJobType.MOVE_RECEIPT)
        .build();
  }

  private static AnnotationConfigApplicationContext context(
      DataSource source,
      Clock clock,
      IntakeJobDispatcher dispatcher,
      ConcurrentLinkedQueue<BackgroundJob> queue) {
    var context = new AnnotationConfigApplicationContext();
    context.setEnvironment(
        new MockEnvironment()
            .withProperty("bookie.db.vendor", "sqlserver")
            .withProperty("bookie.intake.execution-mode", "ON_DEMAND")
            .withProperty("bookie.intake.worker.allowed-job-types", "MOVE_RECEIPT")
            .withProperty("bookie.intake.worker.initial-delay-ms", "0")
            .withProperty("bookie.intake.worker.poll-interval-ms", "100")
            .withProperty("bookie.intake.worker.lease-seconds", "20"));
    context.registerBean(DataSource.class, () -> source);
    BackgroundJobService service = mock(BackgroundJobService.class);
    when(service.claimDue(anyInt(), any(), any()))
        .thenAnswer(invocation -> {
          BackgroundJob next = queue.poll();
          return next == null ? List.of() : List.of(next);
        });
    when(service.hasDueOrBoundWork(any())).thenAnswer(invocation -> !queue.isEmpty());
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
                context.getBean(DurableBackgroundJobWorker.Settings.class),
                clock,
                notFrozen));
    context.register(IntakeWorkerConfiguration.class);
    context.refresh();
    return context;
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
