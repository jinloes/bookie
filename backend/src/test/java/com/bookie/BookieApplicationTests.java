package com.bookie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class BookieApplicationTests {

  @Autowired private Environment environment;

  @Test
  void usesPromotedReadAndJobTypeDefaultsWithTheIntakeWorkerDisabledInTests() {
    assertThat(environment.getProperty("bookie.report-policy.mode")).isEqualTo("NEW");
    assertThat(environment.getProperty("bookie.ledger.read-mode")).isEqualTo("UNIFIED");
    assertThat(environment.getProperty("bookie.reporting.read-mode")).isEqualTo("UNIFIED");
    assertThat(environment.getProperty("bookie.intake.read-mode")).isEqualTo("UNIFIED");
    assertThat(environment.getProperty("bookie.intake.worker.enabled", Boolean.class)).isFalse();
    assertThat(environment.getProperty("bookie.intake.worker.allowed-job-types"))
        .isEqualTo("TRANSLATE_OUTLOOK_ID,PARSE_OUTLOOK,PARSE_RECEIPT,MOVE_RECEIPT");
  }
}
