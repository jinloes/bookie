package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.config.ClockConfiguration;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(ClockConfiguration.class)
class BackgroundJobRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);
  private static final java.util.Set<BackgroundJobType> TYPES =
      java.util.Set.of(BackgroundJobType.MOVE_RECEIPT);

  @Autowired private BackgroundJobRepository repository;

  private void job(
      BackgroundJobState state, LocalDateTime availableAt, LocalDateTime leaseExpiresAt) {
    repository.saveAndFlush(
        BackgroundJob.builder()
            .sourceExternalId("receipt-" + UUID.randomUUID())
            .type(BackgroundJobType.MOVE_RECEIPT)
            .idempotencyKey(UUID.randomUUID().toString())
            .state(state)
            .maxAttempts(11)
            .availableAt(availableAt)
            .leaseOwner(state == BackgroundJobState.LEASED ? "worker" : null)
            .leaseExpiresAt(leaseExpiresAt)
            .createdAt(NOW)
            .updatedAt(NOW)
            .build());
  }

  @Nested
  class ExistsDueOrActiveWork {

    @Test
    void ignoresAvailableRetryScheduledInTheFuture() {
      job(BackgroundJobState.AVAILABLE, NOW.plusHours(6), null);

      assertThat(repository.existsDueOrActiveWork(NOW, TYPES)).isFalse();
    }

    @Test
    void countsAnAvailableJobThatIsDue() {
      job(BackgroundJobState.AVAILABLE, NOW, null);

      assertThat(repository.existsDueOrActiveWork(NOW, TYPES)).isTrue();
    }

    @Test
    void countsALeasedJobWhateverItsDueTime() {
      job(BackgroundJobState.LEASED, NOW.plusHours(6), NOW.plusMinutes(5));

      assertThat(repository.existsDueOrActiveWork(NOW, TYPES)).isTrue();
    }

    @Test
    void ignoresFinishedJobsAndReclaimsExpiredLeases() {
      job(BackgroundJobState.COMPLETED, NOW.minusMinutes(1), null);
      assertThat(repository.existsDueOrActiveWork(NOW, TYPES)).isFalse();

      job(BackgroundJobState.LEASED, NOW.minusMinutes(10), NOW.minusSeconds(1));
      assertThat(
              repository.findClaimableDue(
                  NOW, TYPES, org.springframework.data.domain.PageRequest.of(0, 10)))
          .hasSize(1);
    }
  }
}
