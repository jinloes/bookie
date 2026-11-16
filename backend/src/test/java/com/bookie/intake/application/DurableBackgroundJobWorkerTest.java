package com.bookie.intake.application;

import static com.bookie.intake.application.BackgroundJobServiceTest.CLOCK;
import static com.bookie.intake.application.BackgroundJobServiceTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.ledger.domain.ExpenseSource;
import com.bookie.util.WriteFreezeState;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class DurableBackgroundJobWorkerTest {

  @Mock BackgroundJobService service;
  @Mock IntakeJobDispatcher dispatcher;

  DurableBackgroundJobWorker worker;
  BackgroundJob job;

  @BeforeEach
  void setup() {
    worker = worker(true, Set.of(BackgroundJobType.values()), 0);
    job = fixture(1L, BackgroundJobType.MOVE_RECEIPT);
    job.setState(BackgroundJobState.LEASED);
    job.setLeaseOwner("lease-1");
  }

  DurableBackgroundJobWorker worker(boolean enabled, Set<BackgroundJobType> types, long delay) {
    return worker(enabled, types, delay, false, () -> false);
  }

  DurableBackgroundJobWorker worker(
      boolean enabled,
      Set<BackgroundJobType> types,
      long delay,
      boolean onDemand,
      WriteFreezeState freeze) {
    return new DurableBackgroundJobWorker(
        service,
        dispatcher,
        DurableBackgroundJobWorker.Settings.builder()
            .enabled(enabled)
            .allowedTypes(types)
            .initialDelayMillis(delay)
            .pollIntervalMillis(5000)
            .maxJobsPerPoll(10)
            .leaseSeconds(300)
            .onDemand(onDemand)
            .build(),
        CLOCK,
        freeze);
  }

  @Nested
  class OnDemandAndFreeze {

    final Set<BackgroundJobType> types = Set.of(BackgroundJobType.MOVE_RECEIPT);

    @Test
    void onDemandScheduledRelayIsANoOpButADrainPassPolls() {
      worker = worker(true, types, 0, true, () -> false);
      worker.ready();

      worker.relay();
      verifyNoInteractions(service);

      when(service.claimDue(10, types, Duration.ofSeconds(300))).thenReturn(List.of());
      worker.relayNow();
      verify(service).claimDue(10, types, Duration.ofSeconds(300));
    }

    @Test
    void aFrozenWorkerIgnoresTheRelayAndKicksButADrainPassStillPolls() {
      worker = worker(true, types, 0, false, () -> true);
      worker.ready();

      worker.relay();
      worker.runAvailableJob(null);
      verifyNoInteractions(service);

      when(service.claimDue(10, types, Duration.ofSeconds(300))).thenReturn(List.of());
      worker.relayNow();
      verify(service).claimDue(10, types, Duration.ofSeconds(300));
    }

    @Test
    void aDrainPassBeforeReadyDoesNothingAndDisabledWorkerHasNoWork() {
      worker = worker(true, types, 0, true, () -> false);
      worker.relayNow();
      verifyNoInteractions(service);

      worker = worker(false, types, 0, true, () -> false);
      assertThat(worker.hasDueOrBoundWork()).isFalse();
      verifyNoInteractions(service);
    }

    @Test
    void reportsDueOrBoundWorkFromTheJobService() {
      worker = worker(true, types, 0, true, () -> false);
      when(service.hasDueOrBoundWork(types)).thenReturn(true, false);

      assertThat(worker.hasDueOrBoundWork()).isTrue();
      assertThat(worker.hasDueOrBoundWork()).isFalse();
    }
  }

  @Nested
  class PollingAndExecution {

    @Test
    void readyBarrierAndInitialDelayDoNotBlockPostReadyHints() {
      worker = worker(true, Set.of(job.getType()), 5000);
      worker.relay();
      worker.runAvailableJob(1L);
      verifyNoInteractions(service, dispatcher);

      worker.ready();
      worker.relay();
      verifyNoInteractions(service);

      when(service.claimDue(10, Set.of(job.getType()), Duration.ofSeconds(300)))
          .thenReturn(List.of(job));
      when(dispatcher.execute(job)).thenReturn(JobExecutionResult.completed());
      worker.runAvailableJob(1L);

      verify(dispatcher).execute(job);
      verify(service).finish(eq(1L), eq("lease-1"), argThat(JobExecutionOutcome::successful));
    }

    @Test
    void postCommitHintsRespectAllowedTypesAndStop() {
      worker = worker(true, Set.of(BackgroundJobType.MOVE_RECEIPT), 0);
      worker.ready();
      worker.runAvailableForInboxItem(1L, BackgroundJobType.MOVE_OUTLOOK);
      worker.runAvailableForSource(
          ExpenseSource.RECEIPT, "fixture", BackgroundJobType.MOVE_OUTLOOK);
      verifyNoInteractions(service);

      when(service.claimDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT), Duration.ofSeconds(300)))
          .thenReturn(List.of());
      worker.runAvailableForInboxItem(1L, BackgroundJobType.MOVE_RECEIPT);
      worker.runAvailableForSource(
          ExpenseSource.RECEIPT, "fixture", BackgroundJobType.MOVE_RECEIPT);
      verify(service, times(2))
          .claimDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT), Duration.ofSeconds(300));

      worker.stop();
      worker.relay();
      verify(service, times(2))
          .claimDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT), Duration.ofSeconds(300));
    }

    @Test
    void claimOrFinishInfrastructureFailureEscapesWhileDispatcherFailuresFinishCleanly() {
      worker.ready();
      when(service.claimDue(10, Set.of(BackgroundJobType.values()), Duration.ofSeconds(300)))
          .thenThrow(new DataAccessResourceFailureException("database"))
          .thenReturn(List.of(job));

      assertThatThrownBy(worker::relay).isInstanceOf(DataAccessResourceFailureException.class);
      verifyNoInteractions(dispatcher);

      when(dispatcher.execute(job)).thenThrow(JobExecutionException.retryable("429", null));
      worker.relay();
      verify(service).finish(eq(1L), eq("lease-1"), argThat(outcome -> !outcome.successful()));
    }

    @Test
    void batchesMultipleTranslationJobsAndMarksOmittedMembersTerminal() {
      job.setType(BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      BackgroundJob second = fixture(2L, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      second.setState(BackgroundJobState.LEASED);
      second.setLeaseOwner("lease-2");
      List<BackgroundJob> batch = List.of(job, second);

      worker.ready();
      when(service.claimDue(10, Set.of(BackgroundJobType.values()), Duration.ofSeconds(300)))
          .thenReturn(batch);
      doReturn(Map.of(1L, JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .when(dispatcher)
          .executeBatch(batch);

      worker.relay();

      verify(service).finish(eq(1L), eq("lease-1"), argThat(JobExecutionOutcome::successful));
      verify(service)
          .finish(
              eq(2L),
              eq("lease-2"),
              argThat(outcome ->
                  outcome.failure() instanceof JobExecutionException failure
                      && failure.getKind() == JobExecutionException.FailureKind.TERMINAL));
    }

    @Test
    void batchUnexpectedExceptionFinishesAllMembersAndFinishPersistenceFailureEscapes() {
      job.setType(BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      BackgroundJob second = fixture(2L, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      second.setState(BackgroundJobState.LEASED);
      second.setLeaseOwner("lease-2");
      List<BackgroundJob> batch = List.of(job, second);

      worker.ready();
      when(service.claimDue(10, Set.of(BackgroundJobType.values()), Duration.ofSeconds(300)))
          .thenReturn(batch);
      doThrow(new IllegalStateException("unexpected")).when(dispatcher).executeBatch(batch);
      worker.relay();
      verify(service)
          .finish(
              eq(1L),
              eq("lease-1"),
              argThat(outcome -> outcome.failure().getMessage().equals("unexpected")));
      verify(service)
          .finish(
              eq(2L),
              eq("lease-2"),
              argThat(outcome -> outcome.failure().getMessage().equals("unexpected")));

      when(service.finish(eq(1L), eq("lease-1"), any()))
          .thenThrow(new DataAccessResourceFailureException("commit lost"));
      assertThatThrownBy(worker::relay).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void settingsSnapshotIsImmutableAndValidatesLeaseSeconds() {
      Set<BackgroundJobType> mutable = new HashSet<>(Set.of(BackgroundJobType.MOVE_RECEIPT));
      var settings =
          DurableBackgroundJobWorker.Settings.builder()
              .enabled(true)
              .allowedTypes(mutable)
              .initialDelayMillis(0)
              .pollIntervalMillis(5)
              .maxJobsPerPoll(1)
              .leaseSeconds(10)
              .build();
      mutable.clear();

      assertThat(settings.allows(BackgroundJobType.MOVE_RECEIPT)).isTrue();
      assertThat(settings.leaseSeconds()).isEqualTo(10L);
      assertThatThrownBy(() ->
              DurableBackgroundJobWorker.Settings.builder()
                  .enabled(true)
                  .allowedTypes(Set.of())
                  .initialDelayMillis(0)
                  .pollIntervalMillis(5)
                  .maxJobsPerPoll(1)
                  .leaseSeconds(0)
                  .build())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
