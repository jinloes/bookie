package com.bookie.intake.infrastructure;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.DurableBackgroundJobWorker.Settings;
import com.bookie.util.BackgroundWorkControl;
import com.bookie.util.WriteFreezeState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Runs intake passes only while there is work in {@code ON_DEMAND} mode so an idle app holds no
 * database session and a serverless database can pause. In {@code CONTINUOUS} mode, {@link
 * DurableBackgroundJobWorker}'s scheduled poller runs all session and a drain request triggers an
 * immediate pass.
 */
@Slf4j
public class OnDemandIntakeDrainer implements BackgroundWorkControl, DisposableBean {

  private final DurableBackgroundJobWorker worker;
  private final BackgroundJobService jobService;
  private final WriteFreezeState writeFreeze;
  private final Settings settings;
  private final Clock clock;
  private final Duration quietPeriod;
  private final ScheduledExecutorService executor;
  private ScheduledFuture<?> passes;
  private ScheduledFuture<?> retryWakeup;
  private Instant lastWorkAt;

  @lombok.Builder
  OnDemandIntakeDrainer(
      DurableBackgroundJobWorker worker,
      BackgroundJobService jobService,
      WriteFreezeState writeFreeze,
      Settings settings,
      Clock clock,
      Duration quietPeriod) {
    this.worker = worker;
    this.jobService = jobService;
    this.writeFreeze = writeFreeze;
    this.settings = settings;
    this.clock = clock;
    this.quietPeriod = quietPeriod;
    this.executor =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
          Thread thread = new Thread(runnable, "intake-drain");
          thread.setDaemon(true);
          return thread;
        });
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    if (!settings.enabled()) {
      return;
    }
    if (!settings.onDemand()) {
      worker.ready();
    } else if (!writeFreeze.isFrozen()) {
      startDraining();
    }
  }

  @Override
  public void requestDrain() {
    if (writeFreeze.isFrozen()) {
      return;
    }
    if (settings.onDemand()) {
      startDraining();
    } else {
      worker.runAvailableJob(null);
    }
  }

  @Override
  public boolean drainUntilIdle(Duration timeout) {
    if (!settings.enabled()) {
      return true;
    }
    long deadline = System.nanoTime() + timeout.toNanos();
    if (settings.onDemand()) {
      startDraining();
    }
    while (true) {
      worker.relayNow();
      if (!worker.hasDueOrBoundWork()) {
        return true;
      }
      if (System.nanoTime() - deadline >= 0) {
        return false;
      }
      try {
        TimeUnit.MILLISECONDS.sleep(settings.pollIntervalMillis());
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  @Override
  public int releaseEngineBindings() {
    return jobService.releaseEngineBindings();
  }

  public synchronized boolean isDraining() {
    return passes != null;
  }

  private synchronized void startDraining() {
    if (!settings.enabled()) {
      return;
    }
    if (retryWakeup != null) {
      retryWakeup.cancel(false);
      retryWakeup = null;
    }
    lastWorkAt = clock.instant();
    if (passes == null) {
      worker.ready();
      passes =
          executor.scheduleWithFixedDelay(
              this::pass, 0, settings.pollIntervalMillis(), TimeUnit.MILLISECONDS);
    } else {
      executor.execute(this::pass);
    }
  }

  private void pass() {
    try {
      worker.relayNow();
      boolean busy = worker.hasDueOrBoundWork();
      finishPass(busy);
    } catch (RuntimeException failure) {
      log.warn("Intake drain pass failed", failure);
    }
  }

  private synchronized void finishPass(boolean busy) {
    Instant now = clock.instant();
    if (busy) {
      lastWorkAt = now;
    } else if (passes != null && !now.isBefore(lastWorkAt.plus(quietPeriod))) {
      passes.cancel(false);
      passes = null;
      worker.stop();
      if (!writeFreeze.isFrozen()) {
        jobService.nextRetryDelay(settings.allowedTypes()).ifPresent(this::scheduleRetryWakeup);
      }
    }
  }

  private synchronized void scheduleRetryWakeup(Duration delay) {
    if (retryWakeup != null) {
      retryWakeup.cancel(false);
    }
    retryWakeup =
        executor.schedule(
            () -> {
              if (!writeFreeze.isFrozen()) {
                startDraining();
              }
            },
            Math.max(1L, delay.toMillis()),
            TimeUnit.MILLISECONDS);
  }

  @Override
  public void destroy() {
    executor.shutdownNow();
    worker.stop();
  }
}
