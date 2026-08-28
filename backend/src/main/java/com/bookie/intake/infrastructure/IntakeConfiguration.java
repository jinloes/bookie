package com.bookie.intake.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class IntakeConfiguration {

  @Bean
  Clock intakeClock() {
    return Clock.systemDefaultZone();
  }
}
