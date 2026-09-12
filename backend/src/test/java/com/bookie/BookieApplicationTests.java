package com.bookie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class BookieApplicationTests {

  @Autowired private Environment environment;
  @Autowired private org.jobrunr.storage.StorageProvider storage;
  @Autowired private com.bookie.intake.application.DurableBackgroundJobWorker.Settings settings;

  @Test
  void usesPromotedReadAndJobTypeDefaultsWithTheIntakeWorkerDisabledInTests() {
    assertThat(environment.getProperty("bookie.report-policy.mode")).isEqualTo("NEW");
    assertThat(environment.getProperty("bookie.ledger.read-mode")).isEqualTo("UNIFIED");
    assertThat(environment.getProperty("bookie.reporting.read-mode")).isEqualTo("UNIFIED");
    assertThat(environment.getProperty("bookie.intake.read-mode")).isEqualTo("UNIFIED");
    assertThat(environment.getProperty("bookie.intake.worker.enabled", Boolean.class)).isFalse();
    assertThat(environment.getProperty("bookie.intake.worker.allowed-job-types"))
        .isEqualTo("TRANSLATE_OUTLOOK_ID,PARSE_OUTLOOK,PARSE_RECEIPT,MOVE_RECEIPT");
    assertThat(settings.enabled()).isFalse();
    assertThat(storage).isInstanceOf(org.jobrunr.storage.sql.h2.H2StorageProvider.class);
    assertThat(storage.getBackgroundJobServers()).isEmpty();
    assertThat(environment.getProperty("jobrunr.dashboard.enabled", Boolean.class)).isFalse();
    assertThat(
            environment.getProperty(
                "jobrunr.miscellaneous.allow-anonymous-data-usage", Boolean.class))
        .isFalse();
    assertThat(environment.getProperty("jobrunr.database.skip-create", Boolean.class)).isTrue();
    assertThat(environment.containsProperty("jobrunr.jobs.default-number-of-retries")).isFalse();
    assertThat(environment.containsProperty("jobrunr.jobs.retry-back-off-time-seed")).isFalse();
  }
}
