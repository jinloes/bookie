package com.bookie.intake.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DurableBackgroundJobWorkerTest {

  @Mock private BackgroundJobService jobService;
  @Mock private IntakeJobDispatcher dispatcher;

  @InjectMocks private DurableBackgroundJobWorker worker;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(worker, "leaseSeconds", 60L);
    ReflectionTestUtils.setField(worker, "maxJobsPerPoll", 10);
  }

  @Nested
  class Poll {

    @Test
    void schedulerIsDisabledByDefault() {
      ReflectionTestUtils.setField(worker, "enabled", false);

      worker.poll();

      verify(jobService, never()).recoverExpiredLeases();
      verify(jobService, never()).claimNext(anyString(), any(Duration.class));
    }

    @Test
    void enabledSchedulerRecoversLeasesAndDrainsAvailableJobs() {
      ReflectionTestUtils.setField(worker, "enabled", true);
      BackgroundJob job =
          BackgroundJob.builder().id(1L).type(BackgroundJobType.PARSE_OUTLOOK).build();
      when(jobService.claimNext(anyString(), any(Duration.class)))
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
      when(jobService.claimNext(anyString(), any(Duration.class)))
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
      when(jobService.claim(any(), anyString(), any(Duration.class))).thenReturn(Optional.of(job));
      RuntimeException failure = new RuntimeException("Graph 500");
      when(dispatcher.execute(job)).thenThrow(failure);

      worker.runAvailableJob(2L);

      verify(jobService).fail(any(), anyString(), any(RuntimeException.class));
    }

    @Test
    void successfulRemoteEffectWithFailedStatusUpdateLeavesRetryableLeaseEvidence() {
      BackgroundJob job =
          BackgroundJob.builder().id(3L).type(BackgroundJobType.MOVE_OUTLOOK).build();
      when(jobService.claim(any(), anyString(), any(Duration.class))).thenReturn(Optional.of(job));
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
  }
}
