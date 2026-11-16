package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.DurableBackgroundJobWorker.Settings;
import com.bookie.intake.domain.BackgroundJobType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OnDemandIntakeDrainerTest {

  private static final Duration QUIET_PERIOD = Duration.ofSeconds(60);

  @Mock private DurableBackgroundJobWorker worker;
  @Mock private BackgroundJobService jobService;

  private final AtomicBoolean frozen = new AtomicBoolean();
  private final MutableClock clock = new MutableClock(Instant.parse("2026-09-11T12:00:00Z"));
  private OnDemandIntakeDrainer drainer;

  @AfterEach
  void shutDown() {
    if (drainer != null) {
      drainer.destroy();
    }
  }

  private OnDemandIntakeDrainer drainer(boolean enabled, boolean onDemand) {
    drainer =
        OnDemandIntakeDrainer.builder()
            .worker(worker)
            .jobService(jobService)
            .writeFreeze(frozen::get)
            .settings(
                Settings.builder()
                    .enabled(enabled)
                    .allowedTypes(Set.of(BackgroundJobType.MOVE_RECEIPT))
                    .initialDelayMillis(0)
                    .pollIntervalMillis(10)
                    .maxJobsPerPoll(10)
                    .leaseSeconds(20)
                    .onDemand(onDemand)
                    .build())
            .clock(clock)
            .quietPeriod(QUIET_PERIOD)
            .build();
    return drainer;
  }

  @Nested
  class OnReady {

    @Test
    void onDemandCatchesUpOnWorkQueuedWhileClosed() {
      drainer(true, true).onReady();

      verify(worker).ready();
      await().untilAsserted(() -> verify(worker, atLeast(1)).relayNow());
    }

    @Test
    void continuousModeMarksWorkerReadyWithoutStartingDrainSchedule() {
      drainer(true, false).onReady();

      verify(worker).ready();
      assertThat(drainer.isDraining()).isFalse();
    }
  }

  @Nested
  class RequestDrain {

    @Test
    void doesNothingWhileWritesAreFrozen() {
      frozen.set(true);

      drainer(true, true).requestDrain();

      verifyNoInteractions(worker);
      assertThat(drainer.isDraining()).isFalse();
    }

    @Test
    void continuousModeKicksTheRelay() {
      drainer(true, false).requestDrain();

      verify(worker).runAvailableJob(null);
    }

    @Test
    void aDisabledWorkerNeverStartsDraining() {
      drainer(false, true).requestDrain();

      verifyNoInteractions(worker);
      assertThat(drainer.isDraining()).isFalse();
    }

    @Test
    void anExtraPassQueuedBeforeTheStopDoesNotStopTheWorkerTwice() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      AtomicInteger relays = new AtomicInteger();
      doAnswer(
              invocation -> {
                if (relays.incrementAndGet() == 1) {
                  release.await();
                }
                return null;
              })
          .when(worker)
          .relayNow();
      when(worker.hasDueOrBoundWork()).thenReturn(false);
      drainer(true, true).requestDrain();
      await().until(() -> relays.get() == 1);

      drainer.requestDrain();
      clock.advance(QUIET_PERIOD);
      release.countDown();

      await().untilAsserted(() -> verify(worker, times(2)).hasDueOrBoundWork());
      await()
          .pollDelay(Duration.ofMillis(50))
          .untilAsserted(() -> {
            verify(worker, times(1)).stop();
            assertThat(drainer.isDraining()).isFalse();
          });
      verify(worker, times(1)).ready();
    }

    @Test
    void stopsTheWorkerOnlyAfterTheQuietPeriodPasses() {
      when(worker.hasDueOrBoundWork()).thenReturn(false);
      drainer(true, true).requestDrain();

      verify(worker).ready();
      await().untilAsserted(() -> verify(worker, atLeast(3)).relayNow());
      assertThat(drainer.isDraining()).isTrue();
      verify(worker, never()).stop();

      clock.advance(QUIET_PERIOD);

      await().until(() -> !drainer.isDraining());
      verify(worker).stop();
    }

    @Test
    void busyPassesExtendTheQuietPeriod() {
      AtomicBoolean busy = new AtomicBoolean(true);
      when(worker.hasDueOrBoundWork()).thenAnswer(invocation -> busy.get());
      drainer(true, true).requestDrain();
      await().untilAsserted(() -> verify(worker, atLeast(1)).hasDueOrBoundWork());

      clock.advance(QUIET_PERIOD);
      await().untilAsserted(() -> verify(worker, atLeast(3)).hasDueOrBoundWork());
      busy.set(false);
      await().untilAsserted(() -> verify(worker, atLeast(6)).hasDueOrBoundWork());

      assertThat(drainer.isDraining()).isTrue();
      verify(worker, never()).stop();

      clock.advance(QUIET_PERIOD);
      await().until(() -> !drainer.isDraining());
    }

    @Test
    void aRepeatedRequestReusesTheRunningDrain() {
      when(worker.hasDueOrBoundWork()).thenReturn(false);
      drainer(true, true).requestDrain();
      await().untilAsserted(() -> verify(worker, atLeast(1)).relayNow());

      drainer.requestDrain();

      verify(worker, times(1)).ready();
      await().untilAsserted(() -> verify(worker, atLeast(2)).relayNow());
      assertThat(drainer.isDraining()).isTrue();
    }

    @Test
    void aRequestAfterTheWorkerStoppedRestartsIt() {
      when(worker.hasDueOrBoundWork()).thenReturn(false);
      drainer(true, true).requestDrain();
      clock.advance(QUIET_PERIOD);
      await().until(() -> !drainer.isDraining());

      drainer.requestDrain();

      verify(worker, times(2)).ready();
      assertThat(drainer.isDraining()).isTrue();
    }

    @Test
    void wakesAutomaticallyForScheduledRetryBeyondQuietPeriod() {
      when(worker.hasDueOrBoundWork()).thenReturn(false);
      when(jobService.nextRetryDelay(Set.of(BackgroundJobType.MOVE_RECEIPT)))
          .thenReturn(Optional.of(Duration.ofMillis(40)), Optional.empty());

      drainer(true, true).requestDrain();
      clock.advance(QUIET_PERIOD);

      await().untilAsserted(() -> verify(worker, times(2)).ready());
    }

    @Test
    void cancelsPendingRetryWakeupWhenManualDrainArrivesFirst() {
      when(worker.hasDueOrBoundWork()).thenReturn(false);
      when(jobService.nextRetryDelay(Set.of(BackgroundJobType.MOVE_RECEIPT)))
          .thenReturn(Optional.of(Duration.ofMinutes(10)));

      drainer(true, true).requestDrain();
      clock.advance(QUIET_PERIOD);
      await().until(() -> !drainer.isDraining());

      drainer.requestDrain();

      assertThat(drainer.isDraining()).isTrue();
      verify(worker, times(2)).ready();
    }

    @Test
    void aFailedPassDoesNotCancelTheSchedule() {
      when(worker.hasDueOrBoundWork()).thenReturn(true);
      doThrow(new IllegalStateException("database unavailable"))
          .doNothing()
          .when(worker)
          .relayNow();

      drainer(true, true).requestDrain();

      await().untilAsserted(() -> verify(worker, atLeast(3)).relayNow());
      assertThat(drainer.isDraining()).isTrue();
    }
  }

  @Nested
  class DrainUntilIdle {

    @Test
    void onDemandStartsTheWorkerAndReturnsOnceIdle() {
      when(worker.hasDueOrBoundWork()).thenReturn(true, false);

      assertThat(drainer(true, true).drainUntilIdle(Duration.ofSeconds(5))).isTrue();

      verify(worker).ready();
    }

    @Test
    void continuousModeRelaysWithoutStartingDrainSchedule() {
      when(worker.hasDueOrBoundWork()).thenReturn(false);

      assertThat(drainer(true, false).drainUntilIdle(Duration.ofSeconds(5))).isTrue();

      verify(worker).relayNow();
      assertThat(drainer.isDraining()).isFalse();
    }

    @Test
    void ignoresTheFreezeSoTheCutoverCanFinishQueuedWork() {
      frozen.set(true);
      when(worker.hasDueOrBoundWork()).thenReturn(false);

      assertThat(drainer(true, false).drainUntilIdle(Duration.ofSeconds(5))).isTrue();

      verify(worker).relayNow();
    }

    @Test
    void reportsWorkStillRemainingAtTheTimeout() {
      when(worker.hasDueOrBoundWork()).thenReturn(true);

      assertThat(drainer(true, false).drainUntilIdle(Duration.ofMillis(30))).isFalse();

      verify(worker, atLeast(2)).relayNow();
    }

    @Test
    void stopsWaitingWhenInterrupted() {
      when(worker.hasDueOrBoundWork()).thenReturn(true);
      Thread.currentThread().interrupt();

      try {
        assertThat(drainer(true, false).drainUntilIdle(Duration.ofSeconds(5))).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
      verify(worker, times(1)).relayNow();
    }
  }

  @Nested
  class ReleaseEngineBindings {

    @Test
    void delegatesToTheJobService() {
      when(jobService.releaseEngineBindings()).thenReturn(3);

      assertThat(drainer(true, true).releaseEngineBindings()).isEqualTo(3);
    }
  }

  @Nested
  class Destroy {

    @Test
    void shutsDownTheDrainThread() {
      drainer(true, true).destroy();

      ExecutorService executor =
          (ExecutorService) ReflectionTestUtils.getField(drainer, "executor");
      assertThat(executor.isShutdown()).isTrue();
    }
  }

  private static final class MutableClock extends Clock {

    private volatile Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
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
      return instant;
    }
  }
}
