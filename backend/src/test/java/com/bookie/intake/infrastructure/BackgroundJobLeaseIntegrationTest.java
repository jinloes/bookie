package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.model.ExpenseSource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class BackgroundJobLeaseIntegrationTest {

  @Autowired private InboxItemRepository inboxItemRepository;
  @Autowired private BackgroundJobRepository backgroundJobRepository;

  @Nested
  class Claim {

    @Test
    void optimisticCompareAndSetAllowsOnlyOneWorker() {
      LocalDateTime now = LocalDateTime.of(2026, 8, 25, 13, 0);
      BackgroundJob job = backgroundJobRepository.saveAndFlush(availableJob(now));
      Long initialVersion = job.getVersion();

      int first =
          backgroundJobRepository.claim(
              job.getId(),
              initialVersion,
              "worker-a",
              now.plusMinutes(1),
              now,
              BackgroundJobState.AVAILABLE,
              BackgroundJobState.LEASED);
      int second =
          backgroundJobRepository.claim(
              job.getId(),
              initialVersion,
              "worker-b",
              now.plusMinutes(1),
              now,
              BackgroundJobState.AVAILABLE,
              BackgroundJobState.LEASED);

      assertThat(first).isEqualTo(1);
      assertThat(second).isZero();
      BackgroundJob claimed = backgroundJobRepository.findById(job.getId()).orElseThrow();
      assertThat(claimed.getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(claimed.getLeaseOwner()).isEqualTo("worker-a");
      assertThat(claimed.getAttempts()).isEqualTo(1);
    }
  }

  @Nested
  class Recovery {

    @Test
    void releasesOnlyExpiredLeases() {
      LocalDateTime now = LocalDateTime.of(2026, 8, 25, 13, 0);
      BackgroundJob expired = leasedJob("expired", now.minusSeconds(1));
      BackgroundJob active = leasedJob("active", now.plusMinutes(1));
      expired = backgroundJobRepository.saveAndFlush(expired);
      active = backgroundJobRepository.saveAndFlush(active);

      List<BackgroundJob> recovered =
          backgroundJobRepository.findExpiredLeases(BackgroundJobState.LEASED, now);

      assertThat(recovered).extracting(BackgroundJob::getId).containsExactly(expired.getId());
      assertThat(recovered).extracting(BackgroundJob::getAttempts).containsExactly(1);
      assertThat(active.getState()).isEqualTo(BackgroundJobState.LEASED);
    }

    @Test
    void expiredFinalAttemptMustBeRecoveredBeforeItCanBeClaimed() {
      LocalDateTime now = LocalDateTime.of(2026, 8, 25, 13, 0);
      BackgroundJob expired = leasedJob("last-attempt", now.minusSeconds(1));
      expired.setAttempts(5);
      expired = backgroundJobRepository.saveAndFlush(expired);

      int claimed =
          backgroundJobRepository.claim(
              expired.getId(),
              expired.getVersion(),
              "recovery-worker",
              now.plusMinutes(1),
              now,
              BackgroundJobState.AVAILABLE,
              BackgroundJobState.LEASED);

      assertThat(claimed).isZero();
      BackgroundJob stillExpired = backgroundJobRepository.findById(expired.getId()).orElseThrow();
      assertThat(stillExpired.getAttempts()).isEqualTo(5);
      assertThat(stillExpired.getLeaseOwner()).isEqualTo("worker");
    }
  }

  private BackgroundJob availableJob(LocalDateTime now) {
    InboxItem item = inboxItemRepository.saveAndFlush(inboxItem("available"));
    return BackgroundJob.builder()
        .inboxItem(item)
        .type(BackgroundJobType.PARSE_OUTLOOK)
        .idempotencyKey("parse_outlook:" + item.getId())
        .state(BackgroundJobState.AVAILABLE)
        .attempts(0)
        .maxAttempts(5)
        .availableAt(now)
        .build();
  }

  private BackgroundJob leasedJob(String sourceId, LocalDateTime expiresAt) {
    InboxItem item = inboxItemRepository.saveAndFlush(inboxItem(sourceId));
    return BackgroundJob.builder()
        .inboxItem(item)
        .type(BackgroundJobType.MOVE_OUTLOOK)
        .idempotencyKey("move_outlook:" + item.getId())
        .state(BackgroundJobState.LEASED)
        .attempts(1)
        .maxAttempts(5)
        .availableAt(expiresAt.minusMinutes(1))
        .leaseOwner("worker")
        .leaseExpiresAt(expiresAt)
        .build();
  }

  private InboxItem inboxItem(String sourceId) {
    return InboxItem.builder()
        .origin(ExpenseSource.OUTLOOK_EMAIL)
        .legacySourceType(ExpenseSource.OUTLOOK_EMAIL.name())
        .legacySourceId(sourceId)
        .state(InboxState.QUEUED)
        .externalSyncState(ExternalSyncState.NOT_REQUIRED)
        .rawStatus("PROCESSING")
        .classificationAmbiguous(true)
        .build();
  }
}
