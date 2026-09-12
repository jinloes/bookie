package com.bookie.intake.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import com.bookie.model.ExpenseSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DurableBackgroundJobWorkerTest {

  @Mock private BackgroundJobService jobService;
  @Mock private IntakeJobDispatcher dispatcher;

  @InjectMocks private DurableBackgroundJobWorker worker;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(worker, "enabled", true);
    ReflectionTestUtils.setField(worker, "leaseSeconds", 60L);
    ReflectionTestUtils.setField(worker, "maxJobsPerPoll", 10);
    ReflectionTestUtils.setField(worker, "allowedJobTypes", Set.of(BackgroundJobType.values()));
  }

  @Nested
  class Poll {

    @Test
    void disabledSchedulerDoesNotClaimJobs() {
      ReflectionTestUtils.setField(worker, "enabled", false);

      worker.poll();

      verify(jobService, never()).recoverExpiredLeases();
      verify(jobService, never()).claimNext(anyString(), any(Duration.class), any());
    }

    @Test
    void emptyAllowlistKeepsTheSchedulerDisabled() {
      ReflectionTestUtils.setField(worker, "allowedJobTypes", Set.of());

      worker.poll();

      verify(jobService, never()).recoverExpiredLeases();
      verify(jobService, never()).claimNext(anyString(), any(Duration.class), any());
    }

    @Test
    void enabledSchedulerRecoversLeasesAndDrainsAvailableJobs() {
      ReflectionTestUtils.setField(worker, "enabled", true);
      BackgroundJob job =
          BackgroundJob.builder().id(1L).type(BackgroundJobType.PARSE_OUTLOOK).build();
      when(jobService.claimNext(anyString(), any(Duration.class), any()))
          .thenReturn(Optional.of(job))
          .thenReturn(Optional.empty());
      when(dispatcher.execute(job)).thenReturn(JobExecutionResult.completed());

      worker.poll();

      verify(jobService).recoverExpiredLeases();
      verify(dispatcher).execute(job);
      verify(jobService).complete(any(), anyString(), any());
    }

    @Test
    void batchesOutlookTranslationsAndIsolatesPerItemFailure() {
      ReflectionTestUtils.setField(worker, "enabled", true);
      BackgroundJob translated =
          BackgroundJob.builder().id(11L).type(BackgroundJobType.TRANSLATE_OUTLOOK_ID).build();
      BackgroundJob missing =
          BackgroundJob.builder().id(12L).type(BackgroundJobType.TRANSLATE_OUTLOOK_ID).build();
      when(jobService.claimNext(anyString(), any(Duration.class), any()))
          .thenReturn(Optional.of(translated))
          .thenReturn(Optional.of(missing))
          .thenReturn(Optional.empty());
      RuntimeException failure = new RuntimeException("missing translation");
      when(dispatcher.executeBatch(List.of(translated, missing)))
          .thenReturn(
              Map.of(
                  11L,
                  JobExecutionOutcome.succeeded(
                      JobExecutionResult.withImmutableSourceId("immutable")),
                  12L,
                  JobExecutionOutcome.failed(failure)));

      worker.poll();

      verify(dispatcher).executeBatch(List.of(translated, missing));
      verify(jobService).complete(any(), anyString(), any());
      verify(jobService).fail(any(), anyString(), any(RuntimeException.class));
      verify(dispatcher, never()).execute(any());
    }
  }

  @Nested
  class RunAvailableJob {

    @Test
    void dispatcherFailureIsRecordedAgainstTheLease() {
      BackgroundJob job =
          BackgroundJob.builder().id(2L).type(BackgroundJobType.MOVE_OUTLOOK).build();
      when(jobService.claim(any(), anyString(), any(Duration.class), any()))
          .thenReturn(Optional.of(job));
      RuntimeException failure = new RuntimeException("Graph 500");
      when(dispatcher.execute(job)).thenThrow(failure);

      worker.runAvailableJob(2L);

      verify(jobService).fail(any(), anyString(), any(RuntimeException.class));
    }

    @Test
    void successfulRemoteEffectWithFailedStatusUpdateLeavesRetryableLeaseEvidence() {
      BackgroundJob job =
          BackgroundJob.builder().id(3L).type(BackgroundJobType.MOVE_OUTLOOK).build();
      when(jobService.claim(any(), anyString(), any(Duration.class), any()))
          .thenReturn(Optional.of(job));
      JobExecutionResult result = JobExecutionResult.withImmutableSourceId("immutable-message");
      when(dispatcher.execute(job)).thenReturn(result);
      doThrow(new RuntimeException("database status update failed"))
          .when(jobService)
          .complete(any(), anyString(), any());

      worker.runAvailableJob(3L);

      ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
      verify(jobService).fail(any(), anyString(), failure.capture());
      assertThat(failure.getValue())
          .isInstanceOf(JobExecutionException.class)
          .satisfies(
              captured ->
                  assertThat(((JobExecutionException) captured).getKind())
                      .isEqualTo(JobExecutionException.FailureKind.RETRYABLE));
    }

    @Test
    void disabledWorkerCannotRunDirectJobKickoff() {
      ReflectionTestUtils.setField(worker, "enabled", false);

      worker.runAvailableJob(4L);

      verify(jobService, never()).claim(any(), anyString(), any(Duration.class), any());
    }

    @Test
    void directJobClaimCarriesTheConfiguredAllowlist() {
      Set<BackgroundJobType> allowedTypes = Set.of(BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      ReflectionTestUtils.setField(worker, "allowedJobTypes", allowedTypes);

      worker.runAvailableJob(5L);

      verify(jobService).claim(any(), anyString(), any(Duration.class), eq(allowedTypes));
    }
  }

  @Nested
  class RunAvailableByIdentity {

    @Test
    void disallowedLegacyAndSourceKickoffsCannotReachTheQueue() {
      ReflectionTestUtils.setField(
          worker,
          "allowedJobTypes",
          Set.of(
              BackgroundJobType.TRANSLATE_OUTLOOK_ID,
              BackgroundJobType.PARSE_OUTLOOK,
              BackgroundJobType.PARSE_RECEIPT));
      LegacyPendingKey key = new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, 6L);

      worker.runAvailableForLegacy(key, BackgroundJobType.MOVE_RECEIPT);
      worker.runAvailableForSource(
          ExpenseSource.OUTLOOK_EMAIL, "legacy-message", BackgroundJobType.MOVE_OUTLOOK);

      verify(jobService, never()).findLatest(any(), any());
      verify(jobService, never()).findAvailableForSource(any(), anyString(), any());
    }

    @Test
    void allowlistedReceiptMoveKickoffReachesTheQueue() {
      ReflectionTestUtils.setField(
          worker,
          "allowedJobTypes",
          Set.of(
              BackgroundJobType.TRANSLATE_OUTLOOK_ID,
              BackgroundJobType.PARSE_OUTLOOK,
              BackgroundJobType.PARSE_RECEIPT,
              BackgroundJobType.MOVE_RECEIPT));
      BackgroundJob moveJob =
          BackgroundJob.builder().id(8L).type(BackgroundJobType.MOVE_RECEIPT).build();
      when(jobService.findAvailableForSource(
              ExpenseSource.RECEIPT, "drive-item", BackgroundJobType.MOVE_RECEIPT))
          .thenReturn(Optional.of(moveJob));
      when(jobService.claim(eq(8L), anyString(), any(Duration.class), any()))
          .thenReturn(Optional.of(moveJob));

      worker.runAvailableForSource(
          ExpenseSource.RECEIPT, "drive-item", BackgroundJobType.MOVE_RECEIPT);

      verify(dispatcher).execute(moveJob);
    }

    @Test
    void allowedTranslationAndParseKickoffsRunOnlyTheResolvedJobs() {
      ReflectionTestUtils.setField(
          worker,
          "allowedJobTypes",
          Set.of(
              BackgroundJobType.TRANSLATE_OUTLOOK_ID,
              BackgroundJobType.PARSE_OUTLOOK,
              BackgroundJobType.PARSE_RECEIPT));
      BackgroundJob legacyJob =
          BackgroundJob.builder().id(6L).type(BackgroundJobType.PARSE_RECEIPT).build();
      BackgroundJob sourceJob =
          BackgroundJob.builder().id(7L).type(BackgroundJobType.PARSE_OUTLOOK).build();
      LegacyPendingKey key = new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, 6L);
      when(jobService.findLatest(key, BackgroundJobType.PARSE_RECEIPT))
          .thenReturn(Optional.of(legacyJob));
      when(jobService.findAvailableForSource(
              ExpenseSource.OUTLOOK_EMAIL, "legacy-message", BackgroundJobType.PARSE_OUTLOOK))
          .thenReturn(Optional.of(sourceJob));

      worker.runAvailableForLegacy(key, BackgroundJobType.PARSE_RECEIPT);
      worker.runAvailableForSource(
          ExpenseSource.OUTLOOK_EMAIL, "legacy-message", BackgroundJobType.PARSE_OUTLOOK);

      verify(jobService).claim(eq(6L), anyString(), any(Duration.class), any());
      verify(jobService).claim(eq(7L), anyString(), any(Duration.class), any());
    }
  }

  @Nested
  class ConfiguredDefaults {

    @Test
    void compiledAllowlistDefaultEnablesReceiptMovesButNotOutlookMoves() throws Exception {
      String expression =
          DurableBackgroundJobWorker.class
              .getDeclaredField("allowedJobTypes")
              .getAnnotation(Value.class)
              .value();

      assertThat(expression)
          .contains(BackgroundJobType.MOVE_RECEIPT.name())
          .doesNotContain(BackgroundJobType.MOVE_OUTLOOK.name());
    }
  }
}
