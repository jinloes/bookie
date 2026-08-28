package com.bookie.intake.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.BackgroundJobStore.JobCandidate;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.model.ExpenseSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BackgroundJobServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);

  @Mock private BackgroundJobStore jobStore;
  @Mock private InboxItemStore inboxItemStore;
  @Mock private RetrySchedule retrySchedule;
  @Mock private LegacyPendingJobStateWriter legacyPendingJobStateWriter;

  private BackgroundJobService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC);
    service =
        new BackgroundJobService(
            jobStore, inboxItemStore, retrySchedule, legacyPendingJobStateWriter, clock);
    lenient().when(jobStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(inboxItemStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Nested
  class Claim {

    @Test
    void compareAndSetSkipsLostRaceAndClaimsNextJob() {
      BackgroundJob claimed = leasedJob(2L, BackgroundJobType.PARSE_OUTLOOK);
      when(jobStore.findClaimable(NOW, 20))
          .thenReturn(List.of(new JobCandidate(1L, 3L), new JobCandidate(2L, 4L)));
      when(jobStore.claim(1L, 3L, "worker-a", NOW.plusSeconds(60), NOW)).thenReturn(false);
      when(jobStore.claim(2L, 4L, "worker-a", NOW.plusSeconds(60), NOW)).thenReturn(true);
      when(jobStore.findById(2L)).thenReturn(Optional.of(claimed));

      Optional<BackgroundJob> result = service.claimNext("worker-a", Duration.ofSeconds(60));

      assertThat(result).contains(claimed);
      assertThat(claimed.getInboxItem().getState()).isEqualTo(InboxState.PROCESSING);
      assertThat(claimed.getInboxItem().getRawStatus()).isEqualTo("PROCESSING");
      verify(legacyPendingJobStateWriter).parsingStarted(claimed);
    }

    @Test
    void returnsEmptyWhenSpecificJobLostTheLeaseRace() {
      BackgroundJob available = availableJob(7L, BackgroundJobType.PARSE_RECEIPT);
      available.setVersion(2L);
      when(jobStore.findById(7L)).thenReturn(Optional.of(available));
      when(jobStore.claim(7L, 2L, "worker-b", NOW.plusSeconds(30), NOW)).thenReturn(false);

      assertThat(service.claim(7L, "worker-b", Duration.ofSeconds(30))).isEmpty();
    }
  }

  @Nested
  class Completion {

    @Test
    void recordsImmutableIdentityAndExternalSuccess() {
      BackgroundJob job = leasedJob(3L, BackgroundJobType.MOVE_OUTLOOK);
      job.getInboxItem().setErrorMessage("previous retry");
      when(jobStore.findById(3L)).thenReturn(Optional.of(job));

      service.complete(3L, "worker", JobExecutionResult.withImmutableSourceId("immutable-message"));

      assertThat(job.getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(job.getLeaseOwner()).isNull();
      assertThat(job.getInboxItem().getImmutableSourceId()).isEqualTo("immutable-message");
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.SUCCEEDED);
      assertThat(job.getInboxItem().getErrorMessage()).isNull();
    }

    @Test
    void rejectsCompletionByAWorkerThatDoesNotOwnTheLease() {
      BackgroundJob job = leasedJob(3L, BackgroundJobType.MOVE_OUTLOOK);
      when(jobStore.findById(3L)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> service.complete(3L, "other", JobExecutionResult.completed()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("lease");
    }
  }

  @Nested
  class Failure {

    @Test
    void retryableFailureUsesBoundedScheduleAndRequeuesParse() {
      BackgroundJob job = leasedJob(4L, BackgroundJobType.PARSE_OUTLOOK);
      job.setAttempts(2);
      job.getInboxItem().setState(InboxState.FAILED);
      LocalDateTime next = NOW.plusSeconds(15);
      when(jobStore.findById(4L)).thenReturn(Optional.of(job));
      when(retrySchedule.nextAttempt(4L, 2, NOW)).thenReturn(next);

      service.fail(4L, "worker", JobExecutionException.retryable("temporary parser failure", null));

      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(job.getAvailableAt()).isEqualTo(next);
      assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.QUEUED);
      assertThat(job.getInboxItem().getRawStatus()).isEqualTo("PROCESSING");
      assertThat(job.getTerminalReason()).isNull();
      verify(legacyPendingJobStateWriter).parsingRequeued(job);
    }

    @Test
    void poisonJobBecomesVisibleTerminalFailureAtAttemptLimit() {
      BackgroundJob job = leasedJob(5L, BackgroundJobType.PARSE_RECEIPT);
      job.setAttempts(5);
      job.setMaxAttempts(5);
      when(jobStore.findById(5L)).thenReturn(Optional.of(job));

      service.fail(5L, "worker", JobExecutionException.retryable("still failing", null));

      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(job.getTerminalReason()).isEqualTo("MAX_ATTEMPTS");
      assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.FAILED);
      assertThat(job.getInboxItem().getRawStatus()).isEqualTo("FAILED");
      assertThat(job.getLastError()).isEqualTo("still failing");
      verify(legacyPendingJobStateWriter).parsingFailed(job, "still failing");
    }

    @Test
    void graphReconnectFailureRequiresManualReview() {
      BackgroundJob job = leasedJob(6L, BackgroundJobType.MOVE_OUTLOOK);
      when(jobStore.findById(6L)).thenReturn(Optional.of(job));
      service.fail(6L, "worker", JobExecutionException.manualReview("Reconnect Outlook", null));

      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(job.getTerminalReason()).isEqualTo("MANUAL_REVIEW");
      assertThat(job.getInboxItem().getExternalSyncState())
          .isEqualTo(ExternalSyncState.MANUAL_REVIEW);
    }

    @Test
    void graphRateLimitIsRetryable() {
      BackgroundJob job = leasedJob(8L, BackgroundJobType.MOVE_OUTLOOK);
      job.setAttempts(1);
      when(jobStore.findById(8L)).thenReturn(Optional.of(job));
      when(retrySchedule.nextAttempt(8L, 1, NOW)).thenReturn(NOW.plusSeconds(5));
      service.fail(8L, "worker", JobExecutionException.retryable("Graph 429", null));

      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);
    }

    @Test
    void unexpectedFailureIsTerminal() {
      BackgroundJob job = leasedJob(9L, BackgroundJobType.MOVE_RECEIPT);
      when(jobStore.findById(9L)).thenReturn(Optional.of(job));

      service.fail(9L, "worker", new IllegalArgumentException("bad payload"));

      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(job.getTerminalReason()).isEqualTo("TERMINAL");
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.FAILED);
    }

    @Test
    void translationFailurePreservesLegacyPendingErrorField() {
      BackgroundJob job = leasedJob(11L, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      job.getInboxItem().setErrorMessage("legacy parse error");
      when(jobStore.findById(11L)).thenReturn(Optional.of(job));

      service.fail(
          11L,
          "worker",
          JobExecutionException.manualReview("Outlook ID could not be translated", null));

      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(job.getTerminalReason()).isEqualTo("MANUAL_REVIEW");
      assertThat(job.getLastError()).isEqualTo("Outlook ID could not be translated");
      assertThat(job.getInboxItem().getErrorMessage()).isEqualTo("legacy parse error");
    }
  }

  @Nested
  class ManualRetry {

    @Test
    void resetsTerminalJobAndItem() {
      BackgroundJob job = availableJob(10L, BackgroundJobType.PARSE_OUTLOOK);
      job.setState(BackgroundJobState.TERMINAL);
      job.setAttempts(5);
      job.setTerminalReason("MAX_ATTEMPTS");
      job.setLastError("failure");
      job.getInboxItem().setState(InboxState.FAILED);
      when(jobStore.findById(10L)).thenReturn(Optional.of(job));

      BackgroundJob retried = service.retry(10L);

      assertThat(retried.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(retried.getAttempts()).isZero();
      assertThat(retried.getTerminalReason()).isNull();
      assertThat(retried.getInboxItem().getState()).isEqualTo(InboxState.QUEUED);
    }

    @Test
    void rejectsRetryOfNonTerminalJob() {
      BackgroundJob job = availableJob(11L, BackgroundJobType.PARSE_OUTLOOK);
      when(jobStore.findById(11L)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> service.retry(11L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsRetryThatWouldResurrectADismissedInboxItem() {
      BackgroundJob job = availableJob(12L, BackgroundJobType.PARSE_OUTLOOK);
      job.setState(BackgroundJobState.TERMINAL);
      job.setTerminalReason("DISMISSED");
      job.getInboxItem().setState(InboxState.DISMISSED);
      when(jobStore.findById(12L)).thenReturn(Optional.of(job));

      assertThatThrownBy(() -> service.retry(12L))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("dismissed");
    }

    @Test
    void translationRetryPreservesTheLegacyParsingError() {
      BackgroundJob job = availableJob(13L, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      job.setState(BackgroundJobState.TERMINAL);
      job.setTerminalReason("MANUAL_REVIEW");
      job.getInboxItem().setErrorMessage("legacy parse error");
      when(jobStore.findById(13L)).thenReturn(Optional.of(job));

      service.retry(13L);

      assertThat(job.getInboxItem().getErrorMessage()).isEqualTo("legacy parse error");
    }
  }

  @Test
  void startupRecoveryRequeuesAnIncompleteAttemptWithoutResettingItsCount() {
    BackgroundJob job = leasedJob(12L, BackgroundJobType.PARSE_OUTLOOK);
    job.setLeaseExpiresAt(NOW.minusSeconds(1));
    job.setAttempts(4);
    when(jobStore.findExpiredLeases(NOW)).thenReturn(List.of(job));

    assertThat(service.recoverExpiredLeases()).isEqualTo(1);

    assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
    assertThat(job.getAttempts()).isEqualTo(4);
    assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.QUEUED);
    verify(legacyPendingJobStateWriter).parsingRequeued(job);
  }

  @Test
  void startupRecoveryTerminalizesACrashedFinalAttempt() {
    BackgroundJob job = leasedJob(13L, BackgroundJobType.MOVE_RECEIPT);
    job.setLeaseExpiresAt(NOW.minusSeconds(1));
    job.setAttempts(5);
    when(jobStore.findExpiredLeases(NOW)).thenReturn(List.of(job));

    assertThat(service.recoverExpiredLeases()).isEqualTo(1);

    assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
    assertThat(job.getTerminalReason()).isEqualTo("MAX_ATTEMPTS");
    assertThat(job.getAttempts()).isEqualTo(5);
    assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.FAILED);
    assertThat(job.getInboxItem().getErrorMessage()).contains("final attempt");
  }

  @Test
  void startupRecoveryCompletesParseWhoseDurableResultWasAlreadyAccepted() {
    BackgroundJob job = leasedJob(14L, BackgroundJobType.PARSE_RECEIPT);
    job.setLeaseExpiresAt(NOW.minusSeconds(1));
    job.setAttempts(5);
    job.getInboxItem().setState(InboxState.SAVED);
    when(jobStore.findExpiredLeases(NOW)).thenReturn(List.of(job));

    assertThat(service.recoverExpiredLeases()).isEqualTo(1);

    assertThat(job.getState()).isEqualTo(BackgroundJobState.COMPLETED);
    assertThat(job.getAttempts()).isEqualTo(5);
    assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.SAVED);
  }

  private BackgroundJob availableJob(Long id, BackgroundJobType type) {
    InboxItem item =
        InboxItem.builder()
            .id(100L + id)
            .origin(ExpenseSource.OUTLOOK_EMAIL)
            .state(type.isParsing() ? InboxState.QUEUED : InboxState.SAVED)
            .externalSyncState(
                type.isExternalSync() ? ExternalSyncState.PENDING : ExternalSyncState.NOT_REQUIRED)
            .build();
    return BackgroundJob.builder()
        .id(id)
        .inboxItem(item)
        .type(type)
        .state(BackgroundJobState.AVAILABLE)
        .attempts(0)
        .maxAttempts(5)
        .availableAt(NOW)
        .version(0L)
        .build();
  }

  private BackgroundJob leasedJob(Long id, BackgroundJobType type) {
    BackgroundJob job = availableJob(id, type);
    job.setState(BackgroundJobState.LEASED);
    job.setAttempts(1);
    job.setLeaseOwner("worker");
    job.setLeaseExpiresAt(NOW.plusMinutes(1));
    if (type.isParsing()) {
      job.getInboxItem().setState(InboxState.PROCESSING);
    } else if (type.isExternalSync()) {
      job.getInboxItem().setExternalSyncState(ExternalSyncState.PROCESSING);
    }
    return job;
  }
}
