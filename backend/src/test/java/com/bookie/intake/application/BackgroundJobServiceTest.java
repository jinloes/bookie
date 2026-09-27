package com.bookie.intake.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookie.intake.domain.*;
import com.bookie.ledger.domain.ExpenseSource;
import java.time.*;
import java.util.*;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.states.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BackgroundJobServiceTest {
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC);
  static final UUID EXECUTION = UUID.randomUUID();
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
        .executionId(EXECUTION)
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

  @Nested
  class BindDue {
    @Test
    void preservesHistoricalBudgetAndMakesOneImmutableTranslationGroup() {
      BackgroundJob second = fixture(2L, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      job.setType(BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      job.setAttempts(2);
      job.setMaxAttempts(5);
      job.setExecutionId(null);
      second.setExecutionId(null);
      when(store.findUnboundDue(any(), eq(10), any())).thenReturn(List.of(job, second));
      service.bindDue(10, Set.of(job.getType()));
      assertThat(job.getExecutionId()).isEqualTo(second.getExecutionId()).isNotNull();
      assertThat(job.getAttempts()).isEqualTo(2);
      assertThat(job.getExecutionAttemptBase()).isEqualTo(2);
      assertThat(job.getExecutionPreviousMaxAttempts()).isEqualTo(5);
      assertThat(job.getMaxAttempts()).isEqualTo(13);
      assertThat(job.isExecutionStarted()).isFalse();
    }

    @Test
    void skipsEmptyAllowlist() {
      service.bindDue(10, Set.of());
      verifyNoInteractions(store);
    }

    @Test
    void acceptedParsingCompletesWhileExhaustedLegacyWorkDoesNotReceiveNewBudget() {
      BackgroundJob accepted = fixture(2L, BackgroundJobType.PARSE_RECEIPT);
      accepted.getInboxItem().setState(InboxState.READY);
      accepted.setAttempts(2);
      job.setAttempts(5);
      job.setMaxAttempts(5);
      job.setExecutionId(null);
      when(store.findUnboundDue(any(), anyInt(), any())).thenReturn(List.of(job, accepted));
      service.bindDue(10, Set.of(BackgroundJobType.values()));
      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(job.getTerminalReason()).isEqualTo("MAX_ATTEMPTS");
      assertThat(job.getExecutionId()).isNull();
      assertThat(job.getMaxAttempts()).isEqualTo(5);
      assertThat(accepted.getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(accepted.getAttempts()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(
        value = BackgroundJobType.class,
        names = {"PARSE_OUTLOOK", "PARSE_RECEIPT", "MOVE_RECEIPT"})
    void terminalizesAnUnboundJobRefusedForADismissedItemWithoutChangingTheItem(
        BackgroundJobType type) {
      job = dismissedUnbound(type);
      when(store.findUnboundDue(any(), eq(10), any())).thenReturn(List.of(job));
      service.bindDue(10, Set.of(BackgroundJobType.values()));
      assertRefusedForDismissal(job);
    }

    @Test
    void terminalizesALeasedUnboundJobRefusedForADismissedItemAndClearsItsLease() {
      job = dismissedUnbound(BackgroundJobType.MOVE_RECEIPT);
      job.setState(BackgroundJobState.LEASED);
      job.setLeaseOwner("legacy-worker");
      job.setLeaseExpiresAt(LocalDateTime.now(CLOCK));
      when(store.findUnboundDue(any(), eq(10), any())).thenReturn(List.of(job));
      service.bindDue(10, Set.of(BackgroundJobType.values()));
      assertRefusedForDismissal(job);
      assertThat(job.getLeaseOwner()).isNull();
      assertThat(job.getLeaseExpiresAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = BackgroundJobType.class,
        names = {"TRANSLATE_OUTLOOK_ID", "MOVE_OUTLOOK"})
    void bindsOutlookMoveAndTranslationJobsOfADismissedItem(BackgroundJobType type) {
      job = dismissedUnbound(type);
      job.getInboxItem().setExternalSyncState(null);
      when(store.findUnboundDue(any(), eq(10), any())).thenReturn(List.of(job));
      service.bindDue(10, Set.of(BackgroundJobType.values()));
      assertThat(job.getExecutionId()).isNotNull();
      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(job.getTerminalReason()).isNull();
      assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.DISMISSED);
      assertThat(job.getInboxItem().getExternalSyncState())
          .isEqualTo(type == BackgroundJobType.MOVE_OUTLOOK ? ExternalSyncState.PENDING : null);
      verify(store).save(job);
    }
  }

  static BackgroundJob dismissedUnbound(BackgroundJobType type) {
    BackgroundJob dismissed = fixture(1L, type);
    dismissed.setExecutionId(null);
    dismissed.getInboxItem().setOrigin(ExpenseSource.OUTLOOK_EMAIL);
    dismissed.getInboxItem().setState(InboxState.DISMISSED);
    dismissed.getInboxItem().setRawStatus("DISMISSED");
    dismissed.getInboxItem().setErrorMessage("earlier error");
    return dismissed;
  }

  void assertRefusedForDismissal(BackgroundJob refused) {
    assertThat(refused.getState()).isEqualTo(BackgroundJobState.TERMINAL);
    assertThat(refused.getTerminalReason()).isEqualTo("DISMISSED");
    assertThat(refused.getExecutionId()).isNull();
    InboxItem item = refused.getInboxItem();
    assertThat(item.getState()).isEqualTo(InboxState.DISMISSED);
    assertThat(item.getRawStatus()).isEqualTo("DISMISSED");
    assertThat(item.getErrorMessage()).isEqualTo("earlier error");
    assertThat(item.getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);
    verify(store).save(refused);
  }

  @Nested
  class Start {
    @ParameterizedTest
    @EnumSource(BackgroundJobType.class)
    void recordsStartBeforeProviderWithoutLeases(BackgroundJobType type) {
      job = fixture(1L, type);
      job.setExecutionAttemptBase(2);
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      assertThat(service.start(EXECUTION, type, 11)).containsExactly(job);
      assertThat(job.getAttempts()).isEqualTo(13);
      assertThat(job.isExecutionStarted()).isTrue();
      assertThat(job.getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(job.getLeaseOwner()).isNull();
      assertThat(job.getLeaseExpiresAt()).isNull();
      if (type.isParsing()) {
        assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.PROCESSING);
        assertThat(job.getInboxItem().getRawStatus()).isEqualTo("PROCESSING");
      }
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"READY", "SAVED", "SAVE_PENDING", "DISMISSED"})
    void doesNotReparseAcceptedOrDismissedItems(InboxState state) {
      job.setType(BackgroundJobType.PARSE_RECEIPT);
      job.getInboxItem().setState(state);
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      assertThat(service.start(EXECUTION, job.getType(), 1)).isEmpty();
      assertThat(job.isExecutionStarted()).isFalse();
    }

    @Test
    void startsOutlookMoveOfADismissedItemAndFinishRecordsSuccess() {
      job = fixture(1L, BackgroundJobType.MOVE_OUTLOOK);
      job.getInboxItem().setOrigin(ExpenseSource.OUTLOOK_EMAIL);
      job.getInboxItem().setState(InboxState.DISMISSED);
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThat(service.start(EXECUTION, BackgroundJobType.MOVE_OUTLOOK, 1)).containsExactly(job);
      assertThat(job.getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.PROCESSING);
      assertThat(
              service.finish(
                  1L, EXECUTION, JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .isFalse();
      assertThat(job.getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.SUCCEEDED);
      assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.DISMISSED);
    }

    @ParameterizedTest
    @EnumSource(
        value = BackgroundJobType.class,
        names = {"PARSE_OUTLOOK", "PARSE_RECEIPT", "MOVE_RECEIPT"})
    void skipsParsingAndReceiptMoveJobsOfADismissedItem(BackgroundJobType type) {
      job = fixture(1L, type);
      job.getInboxItem().setState(InboxState.DISMISSED);
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThat(service.start(EXECUTION, type, 1)).isEmpty();
      assertThat(
              service.finish(
                  1L, EXECUTION, JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .isFalse();
      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(job.isExecutionStarted()).isFalse();
      verify(store, never()).save(any());
    }

    @Test
    void rejectsTypeMismatchWithoutChangingAnyMember() {
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      assertThatThrownBy(() -> service.start(EXECUTION, BackgroundJobType.PARSE_OUTLOOK, 1))
          .isInstanceOf(org.jobrunr.JobRunrException.class);
      verify(store, never()).save(any());
    }
  }

  @Nested
  class Finish {
    @Test
    void retryableOutcomesIgnoreReportingCeilingAndDoNotSchedule() {
      job.setAttempts(11);
      LocalDateTime due = job.getAvailableAt();
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThat(
              service.finish(
                  1L,
                  EXECUTION,
                  JobExecutionOutcome.failed(JobExecutionException.retryable("rate limit", null))))
          .isTrue();
      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(job.getAvailableAt()).isEqualTo(due);
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);
      assertThat(job.getLastError()).isEqualTo("rate limit");
    }

    @ParameterizedTest
    @EnumSource(JobExecutionException.FailureKind.class)
    void preservesFailureTaxonomy(JobExecutionException.FailureKind kind) {
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      boolean retry =
          service.finish(
              1L,
              EXECUTION,
              JobExecutionOutcome.failed(new JobExecutionException(kind, "x".repeat(2200), null)));
      assertThat(retry).isEqualTo(kind == JobExecutionException.FailureKind.RETRYABLE);
      assertThat(job.getLastError()).hasSize(2000);
      if (!retry) {
        assertThat(job.getTerminalReason()).isEqualTo(kind.name());
      }
    }

    @Test
    void successfulOutcomeRetainsIdentityAndFreezesCounter() {
      job.setAttempts(4);
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThat(
              service.finish(
                  1L,
                  EXECUTION,
                  JobExecutionOutcome.succeeded(
                      JobExecutionResult.withImmutableSourceId("immutable"))))
          .isFalse();
      assertThat(job.getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(job.getAttempts()).isEqualTo(4);
      assertThat(job.getInboxItem().getImmutableSourceId()).isEqualTo("immutable");
      assertThat(job.getInboxItem().getExternalSyncState()).isEqualTo(ExternalSyncState.SUCCEEDED);
    }

    @Test
    void unexpectedDispatcherFailureIsBusinessTerminal() {
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThat(
              service.finish(
                  1L, EXECUTION, JobExecutionOutcome.failed(new IllegalArgumentException())))
          .isFalse();
      assertThat(job.getTerminalReason()).isEqualTo("TERMINAL");
      assertThat(job.getLastError()).isEqualTo(IllegalArgumentException.class.getName());
    }

    @Test
    void staleGenerationCannotOverwriteReset() {
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThat(
              service.finish(
                  1L,
                  UUID.randomUUID(),
                  JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .isFalse();
      verify(store, never()).save(any());
    }

    @Test
    void persistenceFailuresEscape() {
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      when(store.save(job)).thenThrow(new IllegalStateException("database"));
      assertThatThrownBy(
              () ->
                  service.finish(
                      1L, EXECUTION, JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .hasMessage("database");
    }
  }

  @Nested
  class Projection {
    @Test
    void missingStartedWorkNeedsManualReviewButUnstartedWorkCanBeDelivered() {
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      service.project(EXECUTION, null, Map.of(1L, 0L));
      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      job.setExecutionStarted(true);
      service.project(EXECUTION, null, Map.of(1L, 0L));
      assertThat(job.getTerminalReason()).isEqualTo("MANUAL_REVIEW");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 11})
    void onlyActualExhaustionProjectsMaxAttempts(int failures) {
      Job engine = engine();
      for (int i = 0; i < failures; i++) {
        engine.failed("failure", new IllegalStateException("temporary"));
        if (i + 1 < failures) {
          engine.enqueue();
        }
      }
      job.setExecutionAttemptBase(2);
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      service.project(EXECUTION, engine, Map.of(1L, 0L));
      assertThat(job.getAttempts()).isEqualTo(2 + failures);
      assertThat(job.getTerminalReason())
          .isEqualTo(failures == 11 ? "MAX_ATTEMPTS" : "MANUAL_REVIEW");
    }

    @Test
    void scheduledProjectionUsesEngineTimestampAndError() {
      Job engine = engine();
      engine.failed("failure", new IllegalStateException("retry error"));
      engine.scheduleAt(CLOCK.instant().plusSeconds(3), "retry");
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      service.project(EXECUTION, engine, Map.of(1L, 0L));
      assertThat(job.getAvailableAt()).isEqualTo(LocalDateTime.now(CLOCK).plusSeconds(3));
      assertThat(job.getAttempts()).isEqualTo(1);
      assertThat(job.getLastError()).isEqualTo("retry error");
    }

    @Test
    void staleProjectionAndCompletedBusinessStateWinOverEngineSnapshot() {
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      job.setExecutionStarted(true);
      service.project(EXECUTION, null, Map.of(1L, 1L));
      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      job.setState(BackgroundJobState.COMPLETED);
      service.project(EXECUTION, null, Map.of(1L, 0L));
      assertThat(job.getState()).isEqualTo(BackgroundJobState.COMPLETED);
      verify(store, never()).save(any());
    }
  }

  @Nested
  class Retry {
    @Test
    void resetsOnlyAnExplicitTerminalGeneration() {
      job.setState(BackgroundJobState.TERMINAL);
      job.setAttempts(13);
      job.setExecutionAttemptBase(2);
      job.setExecutionPreviousMaxAttempts(5);
      job.setExecutionStarted(true);
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      service.retry(1L);
      assertThat(job.getExecutionId()).isNull();
      assertThat(job.isExecutionStarted()).isFalse();
      assertThat(job.getExecutionAttemptBase()).isZero();
      assertThat(job.getExecutionPreviousMaxAttempts()).isNull();
      assertThat(job.getAttempts()).isZero();
      assertThat(job.getMaxAttempts()).isEqualTo(11);
      InOrder order = inOrder(store, events);
      order.verify(store).save(job);
      order.verify(events).publishEvent(new InboxItemQueued(1L));
    }

    @Test
    void missingActiveAndDismissedJobsPreserveGuards() {
      when(store.findForUpdate(1L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.retry(1L))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(
              failure ->
                  assertThat(((ResponseStatusException) failure).getStatusCode().value())
                      .isEqualTo(404));
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThatThrownBy(() -> service.retry(1L)).isInstanceOf(ResponseStatusException.class);
      job.setState(BackgroundJobState.TERMINAL);
      job.getInboxItem().setState(InboxState.DISMISSED);
      assertThatThrownBy(() -> service.retry(1L)).isInstanceOf(ResponseStatusException.class);
      verifyNoInteractions(events);
    }
  }

  @Nested
  class ChangedBranchControls {
    @Test
    void readPortsAndSourceSelectionNeverResetOrBindExistingJobs() {
      when(store.findLatest(1L, job.getType())).thenReturn(Optional.of(job));
      when(store.findByInboxItemId(1L)).thenReturn(List.of(job));
      when(store.findActiveExecutionIds()).thenReturn(List.of(EXECUTION));
      assertThat(service.findLatest(1L, job.getType())).contains(job);
      assertThat(service.findByInboxItemId(1L)).containsExactly(job);
      assertThat(service.activeExecutionIds()).containsExactly(EXECUTION);
      assertThat(service.findAvailableForSource(ExpenseSource.RECEIPT, "absent", job.getType()))
          .isEmpty();
      when(items.findBySourceIdentity(ExpenseSource.RECEIPT, "present"))
          .thenReturn(Optional.of(job.getInboxItem()));
      assertThat(
              service.findAvailableForSource(
                  ExpenseSource.RECEIPT, "present", BackgroundJobType.PARSE_RECEIPT))
          .isEmpty();
      assertThat(service.findAvailableForSource(ExpenseSource.RECEIPT, "present", job.getType()))
          .contains(job);
      job.setState(BackgroundJobState.LEASED);
      assertThat(service.findAvailableForSource(ExpenseSource.RECEIPT, "present", job.getType()))
          .isEmpty();
      verify(store, never()).save(any());
      assertThat(job.getExecutionId()).isEqualTo(EXECUTION);
      assertThat(
              service.finish(
                  42L, EXECUTION, JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .isFalse();
    }

    @Test
    void dismissedTerminalReasonCannotBeRetried() {
      job.setState(BackgroundJobState.TERMINAL);
      job.setTerminalReason("DISMISSED");
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThatThrownBy(() -> service.retry(1L)).isInstanceOf(ResponseStatusException.class);
      verify(store, never()).save(any());
      verifyNoInteractions(events);
    }

    @Test
    void retainedProcessingProjectionKeepsEngineOrdinalWithoutInventingApplicationLease() {
      Job engine =
          new Job(
              EXECUTION,
              engine().getJobDetails(),
              new ProcessingState(UUID.randomUUID(), "retained"));
      job.setExecutionAttemptBase(2);
      job.setLeaseOwner("obsolete");
      job.setLeaseExpiresAt(LocalDateTime.now(CLOCK));
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      service.project(EXECUTION, engine, Map.of(1L, 0L));
      assertThat(job.getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(job.getAttempts()).isEqualTo(3);
      assertThat(job.getLeaseOwner()).isNull();
      assertThat(job.getLeaseExpiresAt()).isNull();
      assertThat(job.getTerminalReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
        value = StateName.class,
        names = {"ENQUEUED", "SCHEDULED", "SUCCEEDED", "DELETED"})
    void distinguishesNeverStartedDeliveryFromRetainedEngineAnomalies(StateName state) {
      Job engine = engine();
      if (state == StateName.SCHEDULED) {
        engine.scheduleAt(CLOCK.instant(), "fixture");
      } else if (state == StateName.SUCCEEDED) {
        engine =
            new Job(
                EXECUTION,
                0,
                engine.getJobDetails(),
                List.of(new EnqueuedState(), new ProcessingState(UUID.randomUUID(), "fixture")),
                new java.util.concurrent.ConcurrentHashMap<>());
        engine.succeeded();
      } else if (state == StateName.DELETED) {
        engine.delete("fixture");
      }
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      service.project(EXECUTION, engine, Map.of(1L, 0L));
      if (state == StateName.DELETED || state == StateName.SUCCEEDED) {
        assertThat(job.getTerminalReason()).isEqualTo("MANUAL_REVIEW");
      } else {
        assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
        assertThat(job.getTerminalReason()).isNull();
      }
    }

    @Test
    void nonretryableEngineFailureAndAcceptedParseUseDistinctOutcomes() {
      Job engine = engine();
      engine.failed(
          "invalid", org.jobrunr.JobRunrException.problematicException("unsupported", null));
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      service.project(EXECUTION, engine, Map.of(1L, 0L));
      assertThat(job.getTerminalReason()).isEqualTo("MANUAL_REVIEW");
      job = fixture(1L, BackgroundJobType.PARSE_RECEIPT);
      job.getInboxItem().setState(InboxState.READY);
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      service.project(EXECUTION, null, Map.of(1L, 0L));
      assertThat(job.getState()).isEqualTo(BackgroundJobState.COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(
        value = BackgroundJobState.class,
        names = {"COMPLETED", "TERMINAL"})
    void finishedMembersAreNotReboundStartedOrCompletedAgain(BackgroundJobState state) {
      job.setState(state);
      when(store.findUnboundDue(any(), anyInt(), any())).thenReturn(List.of(job));
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(job));
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      service.bindDue(1, Set.of(job.getType()));
      assertThat(service.start(EXECUTION, job.getType(), 2)).isEmpty();
      assertThat(
              service.finish(
                  1L, EXECUTION, JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .isFalse();
      verify(store, never()).save(any());
    }

    @Test
    void singletonAdoptionAndParsingFailureBranchesPreserveLegacyProjection() {
      job = fixture(1L, BackgroundJobType.PARSE_RECEIPT);
      when(store.findUnboundDue(any(), anyInt(), any())).thenReturn(List.of(job));
      service.bindDue(1, Set.of(job.getType()));
      UUID id = job.getExecutionId();
      assertThat(id).isNotNull().isNotEqualTo(EXECUTION);
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      assertThat(
              service.finish(
                  1L,
                  id,
                  JobExecutionOutcome.failed(JobExecutionException.retryable("temporary", null))))
          .isTrue();
      assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.QUEUED);
      assertThat(
              service.finish(
                  1L,
                  id,
                  JobExecutionOutcome.failed(JobExecutionException.terminal("invalid", null))))
          .isFalse();
      assertThat(job.getInboxItem().getState()).isEqualTo(InboxState.FAILED);
      assertThat(job.getInboxItem().getErrorMessage()).isEqualTo("invalid");
      job.setState(BackgroundJobState.AVAILABLE);
      job.getInboxItem().setState(InboxState.READY);
      assertThat(service.finish(1L, id, JobExecutionOutcome.failed(new IllegalStateException())))
          .isFalse();
      assertThat(job.getState()).isEqualTo(BackgroundJobState.COMPLETED);
    }

    @Test
    void currentParseGuardRejectsStaleAcceptedAndOtherItemsAndClearsThreadContext() {
      service.requireCurrentParse(10L);
      verifyNoInteractions(store);
      job = fixture(10L, BackgroundJobType.PARSE_RECEIPT);
      when(store.findForUpdate(10L)).thenReturn(Optional.of(job));
      BackgroundJobService.enterExecution(job);
      try {
        service.requireCurrentParse(10L);
        assertThatThrownBy(() -> service.requireCurrentParse(11L))
            .hasMessage("Intake parsing result no longer applies");
        job.getInboxItem().setState(InboxState.READY);
        assertThatThrownBy(() -> service.requireCurrentParse(10L))
            .hasMessage("Intake parsing result no longer applies");
        job.setExecutionId(UUID.randomUUID());
        assertThatThrownBy(() -> service.requireCurrentParse(10L))
            .hasMessage("Stale intake parsing generation");
      } finally {
        BackgroundJobService.leaveExecution();
      }
      service.requireCurrentParse(10L);
    }
  }

  @Nested
  class Schedule {
    @Test
    void createsAnAvailableJobKeyedByTypeAndItem() {
      InboxItem item = fixture(5L, BackgroundJobType.MOVE_RECEIPT).getInboxItem();
      when(store.findByIdempotencyKey("move_receipt:5")).thenReturn(Optional.empty());
      when(store.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      BackgroundJob scheduled = service.schedule(item, BackgroundJobType.MOVE_RECEIPT, 2026);

      assertThat(scheduled.getInboxItem()).isSameAs(item);
      assertThat(scheduled.getType()).isEqualTo(BackgroundJobType.MOVE_RECEIPT);
      assertThat(scheduled.getIdempotencyKey()).isEqualTo("move_receipt:5");
      assertThat(scheduled.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(scheduled.getAttempts()).isZero();
      assertThat(scheduled.getMaxAttempts()).isEqualTo(11);
      assertThat(scheduled.getAvailableAt()).isEqualTo(LocalDateTime.now(CLOCK));
      assertThat(scheduled.getTargetYear()).isEqualTo(2026);
    }

    @Test
    void resetsAnExistingGenerationUnlessItIsLeased() {
      job.setState(BackgroundJobState.TERMINAL);
      job.setAttempts(9);
      job.setTerminalReason("MAX_ATTEMPTS");
      when(store.findByIdempotencyKey("move_receipt:1")).thenReturn(Optional.of(job));
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      when(store.save(job)).thenReturn(job);

      service.schedule(job.getInboxItem(), BackgroundJobType.MOVE_RECEIPT, null);

      assertThat(job.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(job.getAttempts()).isZero();
      assertThat(job.getTerminalReason()).isNull();

      job.setState(BackgroundJobState.LEASED);
      job.setAttempts(3);
      service.schedule(job.getInboxItem(), BackgroundJobType.MOVE_RECEIPT, null);

      assertThat(job.getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(job.getAttempts()).isEqualTo(3);
    }

    @Test
    void scheduleIfAbsentLeavesAnExistingGenerationUntouched() {
      job.setType(BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      job.setState(BackgroundJobState.TERMINAL);
      when(store.findByIdempotencyKey("translate_outlook_id:1")).thenReturn(Optional.of(job));
      when(store.findForUpdate(1L)).thenReturn(Optional.of(job));
      when(store.save(job)).thenReturn(job);

      service.scheduleIfAbsent(job.getInboxItem(), BackgroundJobType.TRANSLATE_OUTLOOK_ID);

      assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
    }

    @Test
    void terminalizesActiveJobsForAnItemAtTheClockTime() {
      when(store.terminalizeActiveForInbox(1L, "DISMISSED", LocalDateTime.now(CLOCK)))
          .thenReturn(2);

      assertThat(service.terminalizeActive(1L, "DISMISSED")).isEqualTo(2);
    }
  }

  @Nested
  class InboxLessReceiptMove {

    BackgroundJob inboxLess;

    @BeforeEach
    void inboxLessJob() {
      inboxLess = fixture(7L, BackgroundJobType.MOVE_RECEIPT);
      inboxLess.setInboxItem(null);
      inboxLess.setSourceExternalId("drive-7");
      inboxLess.setTargetYear(2025);
    }

    @Test
    void schedulingCreatesAnAvailableJobKeyedByTransactionAndQueuesItsKick() {
      when(store.findByIdempotencyKey("move_receipt:transaction:41")).thenReturn(Optional.empty());
      when(store.save(any()))
          .thenAnswer(
              invocation -> {
                BackgroundJob saved = invocation.getArgument(0);
                saved.setId(99L);
                return saved;
              });

      BackgroundJob scheduled = service.scheduleReceiptMove(41L, "drive-41", 2025);

      assertThat(scheduled.getInboxItem()).isNull();
      assertThat(scheduled.getSourceExternalId()).isEqualTo("drive-41");
      assertThat(scheduled.getType()).isEqualTo(BackgroundJobType.MOVE_RECEIPT);
      assertThat(scheduled.getTargetYear()).isEqualTo(2025);
      assertThat(scheduled.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(scheduled.getAttempts()).isZero();
      assertThat(scheduled.getMaxAttempts()).isEqualTo(11);
      assertThat(scheduled.getAvailableAt()).isEqualTo(LocalDateTime.now(CLOCK));
      verify(events).publishEvent(new BackgroundJobQueued(99L));
    }

    @Test
    void reschedulingTheSameTransactionReusesItsJob() {
      when(store.findByIdempotencyKey("move_receipt:transaction:7"))
          .thenReturn(Optional.of(inboxLess));

      assertThat(service.scheduleReceiptMove(7L, "drive-7", 2025)).isSameAs(inboxLess);

      verify(store, never()).save(any());
      verify(events).publishEvent(new BackgroundJobQueued(7L));
    }

    @Test
    void aGenerationStartsCompletesAndSavesOnlyTheJob() {
      when(store.findByExecutionId(EXECUTION)).thenReturn(List.of(inboxLess));
      when(store.findForUpdate(7L)).thenReturn(Optional.of(inboxLess));

      assertThat(service.start(EXECUTION, BackgroundJobType.MOVE_RECEIPT, 1))
          .containsExactly(inboxLess);
      assertThat(inboxLess.getState()).isEqualTo(BackgroundJobState.LEASED);
      service.finish(
          7L,
          EXECUTION,
          JobExecutionOutcome.succeeded(JobExecutionResult.withImmutableSourceId("x")));

      assertThat(inboxLess.getState()).isEqualTo(BackgroundJobState.COMPLETED);
      verify(items, never()).save(any());
    }

    @Test
    void retryableAndTerminalFailuresRecordTheirErrorOnTheJob() {
      when(store.findForUpdate(7L)).thenReturn(Optional.of(inboxLess));

      assertThat(
              service.finish(
                  7L,
                  EXECUTION,
                  JobExecutionOutcome.failed(JobExecutionException.retryable("offline", null))))
          .isTrue();
      assertThat(inboxLess.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(inboxLess.getLastError()).isEqualTo("offline");

      service.finish(
          7L,
          EXECUTION,
          JobExecutionOutcome.failed(JobExecutionException.manualReview("missing", null)));
      assertThat(inboxLess.getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(inboxLess.getTerminalReason()).isEqualTo("MANUAL_REVIEW");
      verify(items, never()).save(any());
    }

    @Test
    void aTerminalJobRetriesIntoAFreshGenerationAndKicksByJob() {
      inboxLess.setState(BackgroundJobState.TERMINAL);
      inboxLess.setAttempts(11);
      inboxLess.setLastError("missing");
      when(store.findForUpdate(7L)).thenReturn(Optional.of(inboxLess));

      service.retry(7L);

      assertThat(inboxLess.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(inboxLess.getAttempts()).isZero();
      assertThat(inboxLess.getLastError()).isNull();
      verify(events).publishEvent(new BackgroundJobQueued(7L));
    }

    @Test
    void itIsNeverTreatedAsTheCurrentParse() {
      when(store.findForUpdate(7L)).thenReturn(Optional.of(inboxLess));
      BackgroundJobService.enterExecution(inboxLess);
      try {
        assertThatThrownBy(() -> service.requireCurrentParse(7L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Intake parsing result no longer applies");
      } finally {
        BackgroundJobService.leaveExecution();
      }
    }
  }

  static Job engine() {
    return new Job(EXECUTION, new JobDetails("example.Job", null, "run", List.of()));
  }
}
