package com.bookie.intake.application;

import static com.bookie.intake.application.BackgroundJobServiceTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import java.time.*;
import java.util.*;
import org.jobrunr.JobRunrException;
import org.jobrunr.jobs.*;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.jobs.states.*;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class DurableBackgroundJobWorkerTest {
  @Mock BackgroundJobService service;
  @Mock IntakeJobDispatcher dispatcher;
  @Mock JobScheduler scheduler;
  @Mock StorageProvider storage;
  @Mock JobContext context;
  DurableBackgroundJobWorker worker;
  BackgroundJob job;

  @BeforeEach
  void setup() {
    worker = worker(true, Set.of(BackgroundJobType.values()), 0);
    job = fixture(1L, BackgroundJobType.MOVE_RECEIPT);
  }

  DurableBackgroundJobWorker worker(boolean enabled, Set<BackgroundJobType> types, long delay) {
    return new DurableBackgroundJobWorker(
        service,
        dispatcher,
        scheduler,
        storage,
        DurableBackgroundJobWorker.Settings.builder()
            .enabled(enabled)
            .allowedTypes(types)
            .initialDelayMillis(delay)
            .pollIntervalMillis(5000)
            .maxJobsPerPoll(10)
            .heartbeatTimeoutMultiplier(4)
            .build(),
        CLOCK);
  }

  void callback(BackgroundJobType type, List<BackgroundJob> jobs) {
    worker.ready();
    when(context.getJobId()).thenReturn(EXECUTION);
    when(context.getJobState()).thenReturn(StateName.PROCESSING);
    Job engine = new Job(EXECUTION, details(type), new ProcessingState(UUID.randomUUID(), "test"));
    when(storage.getJobById(EXECUTION)).thenReturn(engine);
    when(service.start(EXECUTION, type, 1)).thenReturn(jobs);
  }

  static JobDetails details(BackgroundJobType type) {
    return new JobDetails(
        DurableBackgroundJobWorker.class.getName(),
        null,
        "executeV1",
        List.of(new JobParameter(String.class, type.name()), JobParameter.JobContext));
  }

  @Nested
  class Publication {
    @Test
    void readyBarrierAndInitialDelayDoNotBlockPostReadyHints() {
      worker = worker(true, Set.of(job.getType()), 5000);
      worker.relay();
      worker.runAvailableJob(1L);
      verifyNoInteractions(service, dispatcher, storage, scheduler);
      worker.ready();
      worker.relay();
      verifyNoInteractions(service);
      worker.runAvailableJob(1L);
      verify(service).bindDue(10, Set.of(job.getType()));
      verifyNoInteractions(dispatcher);
    }

    @Test
    void disabledAndEmptyWorkersCannotPublish() {
      worker = worker(false, Set.of(job.getType()), 0);
      worker.ready();
      worker.relay();
      worker = worker(true, Set.of(), 0);
      worker.ready();
      worker.runAvailableJob(1L);
      verifyNoInteractions(service, storage, dispatcher, scheduler);
    }

    @Test
    void missingNeverStartedBindingPublishesSameUuidAndRetainedRecordIsNotReenqueued() {
      worker.ready();
      when(service.activeExecutionIds()).thenReturn(List.of(EXECUTION));
      when(service.binding(EXECUTION)).thenReturn(List.of(job));
      when(storage.getJobById(EXECUTION))
          .thenThrow(new JobNotFoundException(EXECUTION))
          .thenReturn(new Job(EXECUTION, details(job.getType())));
      worker.relay();
      worker.runAvailableJob(1L);
      verify(scheduler, times(1)).enqueue(eq(EXECUTION), any(IocJobLambda.class));
      verifyNoInteractions(dispatcher);
    }

    @Test
    void lookupFailurePreservesIntentAndDoesNotEnqueue() {
      worker.ready();
      when(service.activeExecutionIds()).thenReturn(List.of(EXECUTION));
      when(service.binding(EXECUTION)).thenReturn(List.of(job));
      when(storage.getJobById(EXECUTION)).thenThrow(new IllegalStateException("storage down"));
      assertThatThrownBy(worker::relay).hasMessage("storage down");
      verify(service, never()).project(any(), any(), any());
      verifyNoInteractions(scheduler, dispatcher);
    }

    @Test
    void missingStartedExecutionCannotBeRecreated() {
      job.setExecutionStarted(true);
      worker.ready();
      when(service.activeExecutionIds()).thenReturn(List.of(EXECUTION));
      when(service.binding(EXECUTION)).thenReturn(List.of(job));
      when(storage.getJobById(EXECUTION)).thenThrow(new JobNotFoundException(EXECUTION));
      worker.relay();
      verifyNoInteractions(scheduler, dispatcher);
      verify(service).project(EXECUTION, null, Map.of(1L, 0L));
    }
  }

  @Nested
  class CallbackAndDeliveryBranches {
    @Test
    void settingsSnapshotIsImmutableAndRejectsTooShortHeartbeatTimeout() {
      Set<BackgroundJobType> mutable = new HashSet<>(Set.of(BackgroundJobType.MOVE_RECEIPT));
      var settings =
          DurableBackgroundJobWorker.Settings.builder()
              .enabled(true)
              .allowedTypes(mutable)
              .initialDelayMillis(0)
              .pollIntervalMillis(5)
              .maxJobsPerPoll(1)
              .heartbeatTimeoutMultiplier(4)
              .build();
      mutable.clear();
      assertThat(settings.allows(BackgroundJobType.MOVE_RECEIPT)).isTrue();
      assertThatThrownBy(
              () ->
                  DurableBackgroundJobWorker.Settings.builder()
                      .enabled(true)
                      .allowedTypes(Set.of())
                      .initialDelayMillis(0)
                      .pollIntervalMillis(5)
                      .maxJobsPerPoll(1)
                      .heartbeatTimeoutMultiplier(3)
                      .build())
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidContextReadinessTypeStateAndPersistedContractNeverReachProviders() {
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), null))
          .isInstanceOf(JobRunrException.class);
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(JobRunrException.class);
      worker.ready();
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(JobRunrException.class);
      when(context.getJobId()).thenReturn(EXECUTION);
      when(context.getJobState()).thenReturn(StateName.ENQUEUED);
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(JobRunrException.class);
      when(context.getJobState()).thenReturn(StateName.PROCESSING);
      when(storage.getJobById(EXECUTION)).thenReturn(new Job(EXECUTION, details(job.getType())));
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(JobRunrException.class);
      when(storage.getJobById(EXECUTION))
          .thenReturn(
              new Job(
                  EXECUTION,
                  details(BackgroundJobType.MOVE_OUTLOOK),
                  new ProcessingState(UUID.randomUUID(), "fixture")));
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(JobRunrException.class);
      worker = worker(true, Set.of(), 0);
      worker.ready();
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(JobRunrException.class);
      verifyNoInteractions(service, dispatcher, scheduler);
    }

    @Test
    void classifierLeavesEveryMalformedSignatureForNativeInvalidInvocationHandling() {
      List<JobParameter> valid = details(job.getType()).getJobParameters();
      List<JobDetails> invalid =
          List.of(
              new JobDetails("other.Worker", null, "executeV1", valid),
              new JobDetails(DurableBackgroundJobWorker.class.getName(), null, "executeV2", valid),
              new JobDetails(
                  DurableBackgroundJobWorker.class.getName(), "INSTANCE", "executeV1", valid),
              new JobDetails(
                  DurableBackgroundJobWorker.class.getName(), null, "executeV1", List.of()),
              new JobDetails(
                  DurableBackgroundJobWorker.class.getName(),
                  null,
                  "executeV1",
                  List.of(new JobParameter(Integer.class, 1), JobParameter.JobContext)),
              new JobDetails(
                  DurableBackgroundJobWorker.class.getName(),
                  null,
                  "executeV1",
                  List.of(
                      new JobParameter(String.class, "MOVE_RECEIPT"),
                      new JobParameter(String.class, "context"))),
              new JobDetails(
                  DurableBackgroundJobWorker.class.getName(),
                  null,
                  "executeV1",
                  List.of(new JobParameter(String.class, null), JobParameter.JobContext)),
              new JobDetails(
                  DurableBackgroundJobWorker.class.getName(),
                  null,
                  "executeV1",
                  List.of(new JobParameter(String.class, "UNKNOWN"), JobParameter.JobContext)));
      for (JobDetails details : invalid) {
        assertThat(DurableBackgroundJobWorker.invocationType(new Job(details))).isEmpty();
      }
      assertThat(DurableBackgroundJobWorker.invocationType(new Job(details(job.getType()))))
          .contains(job.getType());
    }

    @Test
    void postCommitHintsRespectTypesStopAndMissingIneligibleBindings() {
      worker = worker(true, Set.of(BackgroundJobType.MOVE_RECEIPT), 0);
      worker.ready();
      var key =
          new com.bookie.intake.domain.LegacyPendingKey(
              com.bookie.intake.domain.LegacyPendingTable.PENDING_EXPENSES, 1L);
      worker.runAvailableForLegacy(key, BackgroundJobType.MOVE_OUTLOOK);
      worker.runAvailableForSource(
          com.bookie.model.ExpenseSource.RECEIPT, "fixture", BackgroundJobType.MOVE_OUTLOOK);
      verifyNoInteractions(service);
      worker.runAvailableForLegacy(key, BackgroundJobType.MOVE_RECEIPT);
      worker.runAvailableForSource(
          com.bookie.model.ExpenseSource.RECEIPT, "fixture", BackgroundJobType.MOVE_RECEIPT);
      verify(service, times(2)).bindDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT));
      worker.stop();
      worker.relay();
      verify(service, times(2)).bindDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT));
      worker.ready();
      when(service.activeExecutionIds()).thenReturn(List.of(EXECUTION));
      when(storage.getJobById(EXECUTION)).thenThrow(new JobNotFoundException(EXECUTION));
      when(service.binding(EXECUTION)).thenReturn(List.of());
      worker.relay();
      when(service.binding(EXECUTION)).thenReturn(List.of(job));
      job.setState(com.bookie.intake.domain.BackgroundJobState.TERMINAL);
      worker.relay();
      job.setState(com.bookie.intake.domain.BackgroundJobState.AVAILABLE);
      job.getInboxItem().setState(com.bookie.intake.domain.InboxState.DISMISSED);
      worker.relay();
      job.getInboxItem().setState(com.bookie.intake.domain.InboxState.SAVED);
      job.setType(BackgroundJobType.MOVE_OUTLOOK);
      worker.relay();
      verifyNoInteractions(scheduler, dispatcher);
    }

    @Test
    void batchInfrastructureFailureEscapesWhileUnexpectedAndNullOutcomesBecomeTerminal() {
      job.setType(BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      BackgroundJob second = fixture(2L, job.getType());
      List<BackgroundJob> members = List.of(job, second);
      callback(job.getType(), members);
      when(dispatcher.executeBatch(members))
          .thenThrow(new DataAccessResourceFailureException("unavailable"));
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(DataAccessResourceFailureException.class);
      verify(service, never()).finish(any(), any(), any());
      doThrow(new IllegalStateException("unexpected")).when(dispatcher).executeBatch(members);
      worker.executeV1(job.getType().name(), context);
      verify(service)
          .finish(
              eq(1L),
              eq(EXECUTION),
              argThat(outcome -> outcome.failure().getMessage().equals("unexpected")));
      doReturn(null).when(dispatcher).executeBatch(members);
      worker.executeV1(job.getType().name(), context);
      verify(service)
          .finish(
              eq(2L),
              eq(EXECUTION),
              argThat(outcome -> outcome.failure().getMessage().contains("omitted")));
    }
  }

  @Nested
  class Execute {
    @Test
    void commitsOutcomeBeforeThrowingForEngineRetry() {
      callback(job.getType(), List.of(job));
      when(dispatcher.execute(job)).thenThrow(JobExecutionException.retryable("429", null));
      when(service.finish(eq(1L), eq(EXECUTION), any())).thenReturn(true);
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(JobExecutionException.class)
          .hasMessageContaining("retryable members");
      var ordered = inOrder(service, dispatcher);
      ordered.verify(service).start(EXECUTION, job.getType(), 1);
      ordered.verify(dispatcher).execute(job);
      ordered.verify(service).finish(eq(1L), eq(EXECUTION), any());
    }

    @Test
    void completionPersistenceFailureEscapesInsteadOfBecomingDispatcherFailure() {
      callback(job.getType(), List.of(job));
      when(dispatcher.execute(job)).thenReturn(JobExecutionResult.completed());
      when(service.finish(eq(1L), eq(EXECUTION), any()))
          .thenThrow(new IllegalStateException("commit lost"));
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .hasMessage("commit lost");
      verify(service, times(1)).finish(eq(1L), eq(EXECUTION), any());
    }

    @Test
    void infrastructureFailureInsideDispatcherEscapes() {
      callback(job.getType(), List.of(job));
      when(dispatcher.execute(job)).thenThrow(new DataAccessResourceFailureException("database"));
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(DataAccessResourceFailureException.class);
      verify(service, never()).finish(any(), any(), any());
    }

    @Test
    void mixedBatchCommitsAllOutcomesAndMissingMembersAreTerminal() {
      job.setType(BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      BackgroundJob retry = fixture(2L, job.getType());
      BackgroundJob missing = fixture(3L, job.getType());
      List<BackgroundJob> jobs = List.of(job, retry, missing);
      callback(job.getType(), jobs);
      when(dispatcher.executeBatch(jobs))
          .thenReturn(
              Map.of(
                  1L, JobExecutionOutcome.succeeded(JobExecutionResult.completed()),
                  2L, JobExecutionOutcome.failed(JobExecutionException.retryable("503", null))));
      when(service.finish(anyLong(), eq(EXECUTION), any()))
          .thenAnswer(invocation -> invocation.getArgument(0).equals(2L));
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), context))
          .isInstanceOf(JobExecutionException.class);
      verify(service).finish(eq(1L), eq(EXECUTION), argThat(JobExecutionOutcome::successful));
      verify(service)
          .finish(
              eq(3L),
              eq(EXECUTION),
              argThat(
                  outcome ->
                      outcome.failure() instanceof JobExecutionException failure
                          && failure.getKind() == JobExecutionException.FailureKind.TERMINAL));
      verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void unsupportedInvocationsAreUnrecoverableAndCannotCallProviders() {
      assertThatThrownBy(() -> worker.executeV1("UNKNOWN", context))
          .isInstanceOf(JobRunrException.class)
          .satisfies(
              failure ->
                  assertThat(((JobRunrException) failure).isProblematicAndDoNotRetry()).isTrue());
      assertThatThrownBy(() -> worker.executeV1(job.getType().name(), JobContext.Null))
          .isInstanceOf(JobRunrException.class);
      verifyNoInteractions(dispatcher, service);
    }

    @Test
    void staleOrCompletedBindingHasNoProviderCalls() {
      callback(job.getType(), List.of());
      worker.executeV1(job.getType().name(), context);
      verifyNoInteractions(dispatcher);
    }
  }
}
