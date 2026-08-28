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
  void usesPromotedReadDefaultsWithTheIntakeWorkerDisabled() {
    assertThat(environment.getProperty("bookie.report-policy.mode")).isEqualTo("NEW");
    assertThat(environment.getProperty("bookie.ledger.read-mode")).isEqualTo("UNIFIED");
    assertThat(environment.getProperty("bookie.reporting.read-mode")).isEqualTo("UNIFIED");
    assertThat(environment.getProperty("bookie.intake.read-mode")).isEqualTo("UNIFIED");
    assertThat(environment.getProperty("bookie.intake.worker.enabled", Boolean.class)).isFalse();
  }
}
