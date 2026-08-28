package com.bookie.intake.application;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class RetrySchedule {

  private static final Duration BASE_DELAY = Duration.ofSeconds(5);
  private static final Duration MAX_DELAY = Duration.ofMinutes(15);

  public LocalDateTime nextAttempt(Long jobId, int attempts, LocalDateTime now) {
    int exponent = Math.max(0, Math.min(attempts - 1, 10));
    long exponentialSeconds =
        Math.min(MAX_DELAY.toSeconds(), BASE_DELAY.toSeconds() * (1L << exponent));
    long jitterBound = Math.max(1, exponentialSeconds / 4);
    long stableJobId = jobId == null ? 0 : jobId;
    long jitterSeconds = Math.floorMod(stableJobId * 31 + attempts * 17L, jitterBound + 1);
    return now.plusSeconds(exponentialSeconds + jitterSeconds);
  }
}
