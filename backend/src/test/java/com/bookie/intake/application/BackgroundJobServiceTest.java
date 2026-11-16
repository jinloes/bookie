package com.bookie.intake.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BackgroundJobServiceTest {

  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC);
  static final Duration LEASE = Duration.ofSeconds(300);

  @Mock BackgroundJobStore store;
  @Mock InboxItemStore items;
  @Mock ApplicationEventPublisher events;

  BackgroundJobService service;
  BackgroundJob job;

  @BeforeEach
  void setup() {
    service = new BackgroundJobService(store, items, CLOCK, events);
    job = fixture(1L, BackgroundJobType.MOVE_RECEIPT);
  }

  static BackgroundJob fixture(Long id, BackgroundJobType type) {
    return BackgroundJob.builder()
        .id(id)
        .version(0L)
        .type(type)
        .state(BackgroundJobState.AVAILABLE)
        .maxAttempts(11)
        .availableAt(LocalDateTime.now(CLOCK))
        .idempotencyKey(type + ":" + id)
        .inboxItem(
            InboxItem.builder()
                .id(id)
                .origin(ExpenseSource.RECEIPT)
                .state(type.isParsing() ? InboxState.QUEUED : InboxState.SAVED)
                .externalSyncState(ExternalSyncState.PENDING)
                .rawStatus("PROCESSING")
                .build())
        .build();
  }

  static BackgroundJob dismissedUnbound(BackgroundJobType type) {
    BackgroundJob dismissed = fixture(1L, type);
    dismissed.getInboxItem().setOrigin(ExpenseSource.OUTLOOK_EMAIL);
    dismissed.getInboxItem().setState(InboxState.DISMISSED);
    dismissed.getInboxItem().setRawStatus("DISMISSED");
    dismissed.getInboxItem().setErrorMessage("earlier error");
    return dismissed;
  }

  @Nested
  class ClaimDue {

    @Test
    void skipsEmptyAllowlistAndNonPositiveLimit() {
      assertThat(service.claimDue(10, Set.of(), LEASE)).isEmpty();
      assertThat(service.claimDue(0, Set.of(BackgroundJobType.MOVE_RECEIPT), LEASE)).isEmpty();
      verifyNoInteractions(store);
    }

    @ParameterizedTest
    @EnumSource(BackgroundJobType.class)
    void claimsDueJobAssignsLeaseTokenAndIncrementsAttempts(BackgroundJobType type) {
      job = fixture(1L, type);
      job.setAttempts(2);
      when(store.findClaimableDue(any(), eq(10), any())).thenReturn(List.of(job));

      List<BackgroundJob> claimed = service.claimDue(10, Set.of(type), LEASE);

      assertThat(claimed).containsExactly(job);
      assertThat(job.getAttempts()).isEqualTo(3);
      assertThat(job.getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(job.getLeaseOwner()).isNotBlank();
      assertThat(job.getLeaseExpiresAt()).isEqualTo(LocalDateTime.now(CLOCK).plusSeconds(300));
      if (type.isParsing()) {
        assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.PROCESSING);
        assertThat(job.getInboxItem().getRawStatus()).isEqualTo("PROCESSING");
      }
      if (type == BackgroundJobType.MOVE_OUTLOOK || type == BackgroundJobType.MOVE_RECEIPT) {
        assertThat(job.getInboxItem().getExternalSyncState())
            .isEqualTo(ExternalSyncState.PROCESSING);
      }
    }

    @Test
    void acceptedParsingCompletesWhileExhaustedWorkBecomesTerminal() {
      BackgroundJob accepted = fixture(2L, BackgroundJobType.PARSE_RECEIPT);
      accepted.getInboxItem().setState(InboxState.READY);
      accepted.setAttempts(2);
      job.setAttempts(5);
      job.setMaxAttempts(5);
      when(store.findClaimableDue(any(), anyInt(), any())).thenReturn(List.of(job, accepted));

      assertThat(service.claimDue(10, Set.of(BackgroundJobType.values()), LEASE)).isEmpty();

      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(job.getTerminalReason()).isEqualTo("MAX_ATTEMPTS");
      assertThat(job.getLeaseOwner()).isNull();
      assertThat(accepted.getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(accepted.getAttempts()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(
        value = BackgroundJobType.class,
        names = {"PARSE_OUTLOOK", "PARSE_RECEIPT", "MOVE_RECEIPT"})
    void terminalizesRefusedDismissedItemsWithoutChangingTheItem(BackgroundJobType type) {
      job = dismissedUnbound(type);
      job.setState(BackgroundJobState.LEASED);
      job.setLeaseOwner("expired-worker");
      job.setLeaseExpiresAt(LocalDateTime.now(CLOCK).minusSeconds(1));
      when(store.findClaimableDue(any(), eq(10), any())).thenReturn(List.of(job));

      assertThat(service.claimDue(10, Set.of(BackgroundJobType.values()), LEASE)).isEmpty();

      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(job.getTerminalReason()).isEqualTo("DISMISSED");
      assertThat(job.getLeaseOwner()).isNull();
      assertThat(job.getLeaseExpiresAt()).isNull();
      assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.DISMISSED);
    }

    @ParameterizedTest
    @EnumSource(
        value = BackgroundJobType.class,
        names = {"TRANSLATE_OUTLOOK_ID", "MOVE_OUTLOOK"})
    void claimsOutlookMoveAndTranslationJobsOfADismissedItem(BackgroundJobType type) {
      job = dismissedUnbound(type);
      when(store.findClaimableDue(any(), eq(10), any())).thenReturn(List.of(job));

      assertThat(service.claimDue(10, Set.of(BackgroundJobType.values()), LEASE))
          .containsExactly(job);
      assertThat(job.getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(job.getLeaseOwner()).isNotBlank();
    }
  }

  @Nested
  class Finish {

    @BeforeEach
    void leaseJob() {
      job.setState(BackgroundJobState.LEASED);
      job.setLeaseOwner("lease-1");
      job.setAttempts(1);
    }

    @ParameterizedTest
    @CsvSource({"1,3", "2,9", "3,27", "10,59049"})
    void retryableOutcomesScheduleExponentialBackoffBeforeMaxAttempts(
        int attempts, long delaySeconds) {
      job.setAttempts(attempts);
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));

      assertThat(
              service.finish(
                  1L,
                  "lease-1",
                  JobExecutionOutcome.failed(JobExecutionException.retryable("rate limit", null))))
          .isTrue();

      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(job.getLeaseOwner()).isNull();
      assertThat(job.getLeaseExpiresAt()).isNull();
      assertThat(job.getAvailableAt())
          .isEqualTo(LocalDateTime.now(CLOCK).plusSeconds(delaySeconds));
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);
      assertThat(job.getLastError()).isEqualTo("rate limit");
    }

    @Test
    void retryableFailureAtMaxAttemptsBecomesTerminalWithMaxAttemptsReason() {
      job.setAttempts(11);
      job.setMaxAttempts(11);
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));

      assertThat(
              service.finish(
                  1L,
                  "lease-1",
                  JobExecutionOutcome.failed(JobExecutionException.retryable("rate limit", null))))
          .isFalse();

      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(job.getTerminalReason()).isEqualTo("MAX_ATTEMPTS");
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.FAILED);
    }

    @ParameterizedTest
    @EnumSource(JobExecutionException.FailureKind.class)
    void preservesFailureTaxonomyAndTruncatesLongErrors(JobExecutionException.FailureKind kind) {
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));

      boolean retry =
          service.finish(
              1L,
              "lease-1",
              JobExecutionOutcome.failed(new JobExecutionException(kind, "x".repeat(2200), null)));

      assertThat(retry).isEqualTo(kind == JobExecutionException.FailureKind.RETRYABLE);
      assertThat(job.getLastError()).hasSize(2000);
      if (!retry) {
        assertThat(job.getTerminalReason()).isEqualTo(kind.name());
      }
    }

    @Test
    void successfulOutcomeRetainsIdentityAndClearsLease() {
      job.setAttempts(4);
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));

      assertThat(
              service.finish(
                  1L,
                  "lease-1",
                  JobExecutionOutcome.succeeded(
                      JobExecutionResult.withImmutableSourceId("immutable"))))
          .isFalse();

      assertThat(job.getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(job.getAttempts()).isEqualTo(4);
      assertThat(job.getLeaseOwner()).isNull();
      assertThat(job.getInboxItem().getImmutableSourceId()).isEqualTo("immutable");
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.SUCCEEDED);
    }

    @Test
    void staleLeaseCannotOverwriteResetOrAnotherClaim() {
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));

      assertThat(
              service.finish(
                  1L,
                  "different-lease",
                  JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .isFalse();

      verify(store, never()).save(any());
    }
  }

  @Nested
  class CurrentParseGuardAndRetry {

    @Test
    void currentParseGuardRejectsStaleAcceptedAndOtherItemsAndClearsThreadContext() {
      service.requireCurrentParse(10L);
      verifyNoInteractions(store);

      job = fixture(10L, BackgroundJobType.PARSE_RECEIPT);
      job.setState(BackgroundJobState.LEASED);
      job.setLeaseOwner("parse-lease");
      when(store.findForUpdate(10L)).thenReturn(Optional.of(job));

      BackgroundJobService.enterExecution(job);
      try {
        service.requireCurrentParse(10L);
        assertThatThrownBy(() -> service.requireCurrentParse(11L))
            .hasMessage("Intake parsing result no longer applies");
        job.getInboxItem().setState(InboxState.READY);
        assertThatThrownBy(() -> service.requireCurrentParse(10L))
            .hasMessage("Intake parsing result no longer applies");
        job.getInboxItem().setState(InboxState.PROCESSING);
        job.setLeaseOwner("new-lease");
        assertThatThrownBy(() -> service.requireCurrentParse(10L))
            .hasMessage("Stale intake parsing generation");
      } finally {
        BackgroundJobService.leaveExecution();
      }
      service.requireCurrentParse(10L);
    }

    @Test
    void retryResetsOnlyAnExplicitTerminalJobAndPublishesKick() {
      job.setState(BackgroundJobState.TERMINAL);
      job.setAttempts(11);
      job.setLeaseOwner("old");
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));

      service.retry(1L);

      assertThat(job.getLeaseOwner()).isNull();
      assertThat(job.getLeaseExpiresAt()).isNull();
      assertThat(job.getAttempts()).isZero();
      assertThat(job.getMaxAttempts()).isEqualTo(11);
      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      InOrder order = inOrder(store, events);
      order.verify(store).save(job);
      order.verify(events).publishEvent(new InboxItemQueued(1L));
    }

    @Test
    void missingActiveAndDismissedJobsPreserveRetryGuards() {
      when(store.findForUpdate(1L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.retry(1L)).isInstanceOf(ResponseStatusException.class);

      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThatThrownBy(() -> service.retry(1L)).isInstanceOf(ResponseStatusException.class);

      job.setState(BackgroundJobState.TERMINAL);
      job.getInboxItem().setState(InboxState.DISMISSED);
      assertThatThrownBy(() -> service.retry(1L)).isInstanceOf(ResponseStatusException.class);
      verifyNoInteractions(events);
    }
  }

  @Nested
  class ReleaseLeasesAndWorkQueries {

    @Test
    void releaseEngineBindingsReturnsLeasedJobsToAvailable() {
      job.setState(BackgroundJobState.LEASED);
      job.setLeaseOwner("worker");
      job.setLeaseExpiresAt(LocalDateTime.now(CLOCK).plusMinutes(5));
      job.setAvailableAt(LocalDateTime.now(CLOCK).minusDays(1));
      when(store.findByState(BackgroundJobState.LEASED)).thenReturn(List.of(job));

      assertThat(service.releaseEngineBindings()).isEqualTo(1);

      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(job.getAvailableAt()).isEqualTo(LocalDateTime.now(CLOCK));
      assertThat(job.getLeaseOwner()).isNull();
      assertThat(job.getLeaseExpiresAt()).isNull();
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);
      verify(store).save(job);
      verify(items).save(job.getInboxItem());
    }

    @Test
    void hasDueOrBoundWorkChecksAllowedTypesOnly() {
      Set<BackgroundJobType> types = Set.of(BackgroundJobType.MOVE_RECEIPT);
      when(store.existsDueOrActiveWork(LocalDateTime.now(CLOCK), types)).thenReturn(true, false);

      assertThat(service.hasDueOrBoundWork(types)).isTrue();
      assertThat(service.hasDueOrBoundWork(types)).isFalse();
      assertThat(service.hasDueOrBoundWork(Set.of())).isFalse();
    }

    @Test
    void nextRetryDelayComputesFutureBackoffAndClampsNegativeOrEmptyInputs() {
      Set<BackgroundJobType> types = Set.of(BackgroundJobType.PARSE_OUTLOOK);
      LocalDateTime now = LocalDateTime.now(CLOCK);
      when(store.findEarliestFutureAvailableAt(now, types))
          .thenReturn(
              Optional.of(now.plusSeconds(81)), Optional.of(now.minusSeconds(2)), Optional.empty());

      assertThat(service.nextRetryDelay(Set.of())).isEmpty();
      assertThat(service.nextRetryDelay(types)).contains(Duration.ofSeconds(81));
      assertThat(service.nextRetryDelay(types)).contains(Duration.ZERO);
      assertThat(service.nextRetryDelay(types)).isEmpty();
    }

    @Test
    void scheduleAndInboxLessReceiptMoveMaintainIdempotency() {
      when(store.findByIdempotencyKey("move_receipt:transaction:41")).thenReturn(Optional.empty());
      when(store.save(any()))
          .thenAnswer(invocation -> {
            BackgroundJob saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
          });

      BackgroundJob scheduled = service.scheduleReceiptMove(41L, "drive-41", 2025);

      assertThat(scheduled.getInboxItem()).isNull();
      assertThat(scheduled.getSourceExternalId()).isEqualTo("drive-41");
      assertThat(scheduled.getTargetYear()).isEqualTo(2025);
      verify(events).publishEvent(new BackgroundJobQueued(99L));
    }
  }
}
