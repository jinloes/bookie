package com.bookie.intake.infrastructure;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.DurableBackgroundJobWorker.Settings;
import com.bookie.intake.infrastructure.JobRunrIntakeConfiguration.EngineLifecycle;
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
 * Runs intake only while there is work, so an idle app holds no database session and a serverless
 * database can pause. A drain starts the engine and relays every poll interval until no job has
 * been due or bound for the quiet period, then stops the engine; the next drain restarts it. In
 * continuous mode the engine runs all session and a drain request is a plain relay kick.
 */
@Slf4j
public class OnDemandIntakeDrainer implements BackgroundWorkControl, DisposableBean {

  private final EngineLifecycle engine;
  private final DurableBackgroundJobWorker worker;
  private final BackgroundJobService jobService;
  private final WriteFreezeState writeFreeze;
  private final Settings settings;
  private final Clock clock;
  private final Duration quietPeriod;
  private final ScheduledExecutorService executor;
  private ScheduledFuture<?> passes;
  private Instant lastWorkAt;

  @lombok.Builder
  OnDemandIntakeDrainer(
      EngineLifecycle engine,
      DurableBackgroundJobWorker worker,
      BackgroundJobService jobService,
      WriteFreezeState writeFreeze,
      Settings settings,
      Clock clock,
      Duration quietPeriod) {
    this.engine = engine;
    this.worker = worker;
    this.jobService = jobService;
    this.writeFreeze = writeFreeze;
    this.settings = settings;
    this.clock = clock;
    this.quietPeriod = quietPeriod;
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "intake-drain");
              thread.setDaemon(true);
              return thread;
            });
  }

  /** Catches up on work queued while the app was closed. */
  @EventListener(ApplicationReadyEvent.class)
  public void drainAtStartup() {
    if (settings.onDemand()) {
      requestDrain();
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

  synchronized boolean isDraining() {
    return passes != null;
  }

  private synchronized void startDraining() {
    if (!settings.enabled()) {
      return;
    }
    lastWorkAt = clock.instant();
    if (passes == null) {
      engine.start();
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
      // A failed pass must not cancel the schedule; the next pass retries.
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
      engine.stop();
    }
  }

  @Override
  public void destroy() {
    executor.shutdownNow();
  }
}
