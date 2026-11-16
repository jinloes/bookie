package com.bookie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class BookieApplicationTests {

  @Autowired private Environment environment;
  @Autowired private com.bookie.intake.application.DurableBackgroundJobWorker.Settings settings;

  @Test
  void usesJobTypeDefaultsWithTheIntakeWorkerDisabledInTests() {
    assertThat(environment.getProperty("bookie.report-policy.mode")).isNull();
    assertThat(environment.getProperty("bookie.ledger.read-mode")).isNull();
    assertThat(environment.getProperty("bookie.reporting.read-mode")).isNull();
    assertThat(environment.getProperty("bookie.intake.read-mode")).isNull();
    assertThat(environment.getProperty("bookie.intake.worker.enabled", Boolean.class)).isFalse();
    assertThat(environment.getProperty("bookie.intake.worker.allowed-job-types"))
        .isEqualTo("TRANSLATE_OUTLOOK_ID,PARSE_OUTLOOK,PARSE_RECEIPT,MOVE_OUTLOOK,MOVE_RECEIPT");
    assertThat(settings.enabled()).isFalse();
  }
}
