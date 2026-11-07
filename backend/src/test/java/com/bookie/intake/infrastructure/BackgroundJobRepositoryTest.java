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
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(ClockConfiguration.class)
class BackgroundJobRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);

  @Autowired private BackgroundJobRepository repository;

  private void job(BackgroundJobState state, UUID executionId, LocalDateTime availableAt) {
    repository.saveAndFlush(
        BackgroundJob.builder()
            .sourceExternalId("receipt-" + UUID.randomUUID())
            .type(BackgroundJobType.MOVE_RECEIPT)
            .idempotencyKey(UUID.randomUUID().toString())
            .state(state)
            .maxAttempts(11)
            .executionId(executionId)
            .availableAt(availableAt)
            .createdAt(NOW)
            .updatedAt(NOW)
            .build());
  }

  @Nested
  class ExistsBoundWorkDue {

    @Test
    void ignoresABoundRetryScheduledInTheFuture() {
      job(BackgroundJobState.AVAILABLE, UUID.randomUUID(), NOW.plusHours(6));

      assertThat(repository.existsBoundWorkDue(NOW)).isFalse();
      assertThat(repository.findActiveExecutionIds()).hasSize(1);
    }

    @Test
    void countsABoundJobThatIsDue() {
      job(BackgroundJobState.AVAILABLE, UUID.randomUUID(), NOW);

      assertThat(repository.existsBoundWorkDue(NOW)).isTrue();
    }

    @Test
    void countsALeasedBoundJobWhateverItsDueTime() {
      job(BackgroundJobState.LEASED, UUID.randomUUID(), NOW.plusHours(6));

      assertThat(repository.existsBoundWorkDue(NOW)).isTrue();
    }

    @Test
    void ignoresUnboundAndFinishedJobs() {
      job(BackgroundJobState.AVAILABLE, null, NOW.minusMinutes(1));
      job(BackgroundJobState.COMPLETED, UUID.randomUUID(), NOW.minusMinutes(1));

      assertThat(repository.existsBoundWorkDue(NOW)).isFalse();
    }
  }
}
