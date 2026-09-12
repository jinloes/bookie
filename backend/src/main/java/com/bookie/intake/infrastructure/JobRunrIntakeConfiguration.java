package com.bookie.intake.infrastructure;

import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.DurableBackgroundJobWorker.Settings;
import com.bookie.intake.domain.BackgroundJobType;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.server.JobActivator;
import org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration;
import org.jobrunr.spring.autoconfigure.storage.JobRunrSqlStorageAutoConfiguration;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.StorageProviderUtils.DatabaseOptions;
import org.jobrunr.storage.sql.h2.H2StorageProvider;
import org.jobrunr.utils.mapper.JsonMapper;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(
    exclude = {JobRunrAutoConfiguration.class, JobRunrSqlStorageAutoConfiguration.class})
public class JobRunrIntakeConfiguration {

  @Bean
  Settings intakeWorkerSettings(Environment environment) {
    boolean enabled = environment.getProperty("bookie.intake.worker.enabled", Boolean.class, true);
    require(environment, "jobrunr.background-job-server.enabled", Boolean.toString(enabled));
    require(environment, "jobrunr.background-job-server.worker-count", "1");
    require(environment, "jobrunr.background-job-server.poll-interval-in-seconds", "5");
    require(environment, "jobrunr.dashboard.enabled", "false");
    require(environment, "jobrunr.miscellaneous.allow-anonymous-data-usage", "false");
    require(environment, "jobrunr.database.skip-create", "true");
    require(environment, "jobrunr.database.type", "sql");
    require(environment, "jobrunr.database.table-prefix", "");
    require(environment, "jobrunr.database.datasource", "");
    require(environment, "jobrunr.job-scheduler.enabled", "true");
    for (String property :
        List.of(
            "jobrunr.jobs.default-number-of-retries", "jobrunr.jobs.retry-back-off-time-seed")) {
      if (environment.containsProperty(property)) {
        throw new IllegalArgumentException(
            "JobRunr default retry policy must not be overridden: " + property);
      }
    }
    long heartbeatSeconds =
        environment.getProperty("bookie.intake.worker.lease-seconds", Long.class, 300L);
    if (heartbeatSeconds <= 0) {
      throw new IllegalArgumentException("Intake heartbeat timeout must be positive");
    }
    int multiplier = Math.toIntExact(Math.max(4, Math.ceilDiv(heartbeatSeconds, 5)));
    require(
        environment,
        "jobrunr.background-job-server.server-timeout-poll-interval-multiplicand",
        Integer.toString(multiplier));
    Set<BackgroundJobType> allowed =
        Binder.get(environment)
            .bind("bookie.intake.worker.allowed-job-types", Bindable.setOf(BackgroundJobType.class))
            .orElse(
                Set.of(
                    BackgroundJobType.TRANSLATE_OUTLOOK_ID,
                    BackgroundJobType.PARSE_OUTLOOK,
                    BackgroundJobType.PARSE_RECEIPT,
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
        .heartbeatTimeoutMultiplier(multiplier)
        .build();
  }

  @Bean
  JsonMapper intakeJobJsonMapper() {
    return new JacksonJsonMapper();
  }

  @Bean(destroyMethod = "close")
  @DependsOnDatabaseInitialization
  StorageProvider intakeStorageProvider(
      DataSource dataSource, JsonMapper mapper, Settings settings) {
    H2StorageProvider storage = new H2StorageProvider(dataSource, DatabaseOptions.SKIP_CREATE);
    storage.setJobMapper(new JobMapper(mapper));
    return storage;
  }

  @Bean
  JobScheduler intakeJobScheduler(StorageProvider storage) {
    return new JobScheduler(storage);
  }

  @Bean
  EngineLifecycle intakeEngineLifecycle(
      StorageProvider storage,
      JsonMapper mapper,
      ApplicationContext context,
      DurableBackgroundJobWorker worker,
      Settings settings) {
    return new EngineLifecycle(storage, mapper, context, worker, settings);
  }

  private static void require(Environment environment, String property, String expected) {
    if (!expected.equalsIgnoreCase(environment.getProperty(property, expected))) {
      throw new IllegalArgumentException(
          "Unsupported JobRunr setting: " + property + " must be " + expected);
    }
  }

  @lombok.RequiredArgsConstructor
  static class EngineLifecycle implements DisposableBean {
    private final StorageProvider storage;
    private final JsonMapper mapper;
    private final ApplicationContext context;
    private final DurableBackgroundJobWorker worker;
    private final Settings settings;
    private BackgroundJobServer server;

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void ready() {
      if (!settings.enabled() || server != null) {
        return;
      }
      JobActivator activator =
          new JobActivator() {
            @Override
            public <T> T activateJob(Class<T> type) {
              return context.getBean(type);
            }
          };
      server =
          new BackgroundJobServer(
              new JobRunrIntakeStorageProvider(storage, settings),
              mapper,
              activator,
              BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration()
                  .andWorkerCount(1)
                  .andPollIntervalInSeconds(5)
                  .andServerTimeoutPollIntervalMultiplicand(settings.heartbeatTimeoutMultiplier()));
      worker.ready();
      server.start();
    }

    @Override
    public synchronized void destroy() {
      if (server != null) {
        server.stop();
      }
      worker.stop();
    }
  }
}
