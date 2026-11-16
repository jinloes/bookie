package com.bookie.intake.infrastructure;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.DurableBackgroundJobWorker.Settings;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.util.WriteFreezeState;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class IntakeWorkerConfiguration {

  static final Duration DRAIN_QUIET_PERIOD = Duration.ofSeconds(60);

  @Bean
  Settings intakeWorkerSettings(Environment environment) {
    boolean enabled = environment.getProperty("bookie.intake.worker.enabled", Boolean.class, true);
    boolean onDemand =
        switch (environment
            .getProperty("bookie.intake.execution-mode", "CONTINUOUS")
            .trim()
            .toUpperCase(Locale.ROOT)) {
          case "CONTINUOUS" -> false;
          case "ON_DEMAND" -> true;
          default ->
              throw new IllegalArgumentException(
                  "bookie.intake.execution-mode must be CONTINUOUS or ON_DEMAND");
        };
    long leaseSeconds =
        environment.getProperty("bookie.intake.worker.lease-seconds", Long.class, 300L);
    if (leaseSeconds <= 0) {
      throw new IllegalArgumentException("Intake lease timeout must be positive");
    }
    Set<BackgroundJobType> allowed =
        Binder.get(environment)
            .bind("bookie.intake.worker.allowed-job-types", Bindable.setOf(BackgroundJobType.class))
            .orElse(
                Set.of(
                    BackgroundJobType.TRANSLATE_OUTLOOK_ID,
                    BackgroundJobType.PARSE_OUTLOOK,
                    BackgroundJobType.PARSE_RECEIPT,
                    BackgroundJobType.MOVE_OUTLOOK,
                    BackgroundJobType.MOVE_RECEIPT));
    return Settings.builder()
        .enabled(enabled)
        .allowedTypes(allowed)
        .initialDelayMillis(
            environment.getProperty("bookie.intake.worker.initial-delay-ms", Long.class, 5000L))
        .pollIntervalMillis(
            environment.getProperty("bookie.intake.worker.poll-interval-ms", Long.class, 5000L))
        .maxJobsPerPoll(
            environment.getProperty("bookie.intake.worker.max-jobs-per-poll", Integer.class, 10))
        .leaseSeconds(leaseSeconds)
        .onDemand(onDemand)
        .build();
  }

  @Bean
  OnDemandIntakeDrainer intakeDrainer(
      DurableBackgroundJobWorker worker,
      BackgroundJobService jobService,
      WriteFreezeState writeFreeze,
      Settings settings,
      Clock clock) {
    return OnDemandIntakeDrainer.builder()
        .worker(worker)
        .jobService(jobService)
        .writeFreeze(writeFreeze)
        .settings(settings)
        .clock(clock)
        .quietPeriod(DRAIN_QUIET_PERIOD)
        .build();
  }
}
