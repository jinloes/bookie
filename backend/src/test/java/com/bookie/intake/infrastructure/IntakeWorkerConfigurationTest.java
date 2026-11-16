package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.domain.BackgroundJobType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

class IntakeWorkerConfigurationTest {

  private final IntakeWorkerConfiguration configuration = new IntakeWorkerConfiguration();

  @Nested
  class Settings {

    @Test
    void defaultsEnableRemoteMovesAndDeriveLeaseSeconds() {
      var settings = configuration.intakeWorkerSettings(new MockEnvironment());

      assertThat(settings.enabled()).isTrue();
      assertThat(settings.allowedTypes())
          .contains(BackgroundJobType.MOVE_OUTLOOK, BackgroundJobType.MOVE_RECEIPT);
      assertThat(settings.leaseSeconds()).isEqualTo(300L);
      assertThat(settings.initialDelayMillis()).isEqualTo(5000L);
      assertThat(settings.pollIntervalMillis()).isEqualTo(5000L);
      assertThat(settings.maxJobsPerPoll()).isEqualTo(10);
      assertThat(settings.onDemand()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
      "bookie.intake.worker.lease-seconds,0",
      "bookie.intake.worker.max-jobs-per-poll,0",
      "bookie.intake.worker.initial-delay-ms,-1",
      "bookie.intake.worker.poll-interval-ms,0"
    })
    void rejectsInvalidBoundsBeforeWorkerConstruction(String key, String value) {
      assertThatThrownBy(() ->
              configuration.intakeWorkerSettings(new MockEnvironment().withProperty(key, value)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"'',false", "CONTINUOUS,false", "' on_demand ',true", "ON_DEMAND,true"})
    void parsesTheExecutionMode(String mode, boolean onDemand) {
      MockEnvironment environment = new MockEnvironment();
      if (!mode.isEmpty()) {
        environment.setProperty("bookie.intake.execution-mode", mode);
      }

      assertThat(configuration.intakeWorkerSettings(environment).onDemand()).isEqualTo(onDemand);
    }

    @Test
    void rejectsAnUnknownExecutionMode() {
      assertThatThrownBy(() ->
              configuration.intakeWorkerSettings(
                  new MockEnvironment().withProperty("bookie.intake.execution-mode", "LAZY")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("CONTINUOUS or ON_DEMAND");
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
  class DrainerWiring {

    @Test
    void buildsDrainerWithSettingsClockAndDependencies() {
      var settings = configuration.intakeWorkerSettings(new MockEnvironment());
      DurableBackgroundJobWorker worker = mock(DurableBackgroundJobWorker.class);
      var jobService = mock(com.bookie.intake.application.BackgroundJobService.class);
      var drainer =
          configuration.intakeDrainer(
              worker, jobService, () -> false, settings, java.time.Clock.systemUTC());
      try {
        drainer.onReady();
        verify(worker, times(1)).ready();
      } finally {
        drainer.destroy();
      }
    }
  }
}
