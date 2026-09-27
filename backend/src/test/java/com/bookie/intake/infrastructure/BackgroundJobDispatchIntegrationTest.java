package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookie.intake.application.*;
import com.bookie.intake.domain.*;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.intake.sources.InboxItemCreationService;
import com.bookie.intake.sources.ReceiptService;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.ledger.domain.ExpenseSource;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:dispatch_races;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=validate",
      "bookie.intake.worker.enabled=false",
      "bookie.auto-import.enabled=false"
    })
class BackgroundJobDispatchIntegrationTest {
  @Autowired BackgroundJobService service;
  @Autowired BackgroundJobStore store;
  @Autowired InboxItemStore items;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;
  @Autowired InboxItemCreationService creation;
  @Autowired IntakeJobDispatcher dispatcher;
  @Autowired EntityManager entityManager;
  @MockitoBean ReceiptService receipts;
  TransactionTemplate tx;

  @BeforeEach
  void setup() {
    tx = new TransactionTemplate(transactions);
    tx.executeWithoutResult(
        status -> {
          jdbc.update("DELETE FROM background_jobs");
          jdbc.update("DELETE FROM inbox_artifacts");
          jdbc.update("DELETE FROM inbox_items");
        });
  }

  @Nested
  class Generations {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void receiptReplayAfterOutcomeRollbackRetainsIdentityYearAndValidatesChecksum(
        boolean checksumMatchesOnReplay) {
      String sourceId = "receipt-replay-item";
      String checksum = "a".repeat(64);
      int destinationYear = 2024;
      Long id = create(BackgroundJobType.MOVE_RECEIPT, 0, 5);
      tx.executeWithoutResult(
          status -> {
            BackgroundJob job = store.findForUpdate(id).orElseThrow();
            job.setTargetYear(destinationYear);
            job.getInboxItem().setLegacySourceId(sourceId);
            job.getInboxItem()
                .addArtifact(
                    InboxArtifact.builder()
                        .type(InboxArtifactType.RECEIPT)
                        .externalId(sourceId)
                        .sha256(checksum)
                        .build());
            items.save(job.getInboxItem());
            store.save(job);
          });
      service.bindDue(1, Set.of(BackgroundJobType.MOVE_RECEIPT));
      UUID execution = load(id).getExecutionId();
      assertThat(execution).isNotNull();
      when(receipts.hasReceiptChecksum(sourceId, checksum))
          .thenReturn(true, checksumMatchesOnReplay);
      doAnswer(
              invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return null;
              })
          .when(receipts)
          .moveTaxesFolderForJob(anyString(), anyInt());
      var receiptCalls = inOrder(receipts);

      List<BackgroundJob> firstAttempt =
          service.start(execution, BackgroundJobType.MOVE_RECEIPT, 1);
      assertThat(firstAttempt).extracting(BackgroundJob::getId).containsExactly(id);
      JobExecutionResult firstResult = dispatcher.execute(firstAttempt.getFirst());
      receiptCalls.verify(receipts).hasReceiptChecksum(sourceId, checksum);
      receiptCalls.verify(receipts).moveTaxesFolderForJob(sourceId, destinationYear);
      List<Integer> completionStatuses = new ArrayList<>();

      // The remote effect has returned; fail only the local completion transaction.
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        service.finish(id, execution, JobExecutionOutcome.succeeded(firstResult));
                        entityManager.flush();
                        assertThat(
                                jdbc.queryForObject(
                                    "SELECT state FROM background_jobs WHERE id=?",
                                    String.class,
                                    id))
                            .isEqualTo("COMPLETED");
                        TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                              @Override
                              public void beforeCommit(boolean readOnly) {
                                throw new DataAccessResourceFailureException(
                                    "simulated receipt outcome commit failure");
                              }

                              @Override
                              public void afterCompletion(int completionStatus) {
                                completionStatuses.add(completionStatus);
                              }
                            });
                      }))
          .isInstanceOf(DataAccessResourceFailureException.class)
          .hasMessage("simulated receipt outcome commit failure");
      assertThat(completionStatuses).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
      BackgroundJob unfinished = load(id);
      assertThat(unfinished.getExecutionId()).isEqualTo(execution);
      assertThat(unfinished.isExecutionStarted()).isTrue();
      assertThat(unfinished.getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(unfinished.getAttempts()).isEqualTo(1);
      assertThat(unfinished.getTargetYear()).isEqualTo(destinationYear);
      assertThat(unfinished.getInboxItem().getLegacySourceId()).isEqualTo(sourceId);
      assertThat(unfinished.getInboxItem().getExternalSyncState())
          .isEqualTo(ExternalSyncState.PROCESSING);
      assertThat(unfinished.getInboxItem().getArtifacts())
          .extracting(
              InboxArtifact::getType, InboxArtifact::getExternalId, InboxArtifact::getSha256)
          .containsExactly(tuple(InboxArtifactType.RECEIPT, sourceId, checksum));

      List<BackgroundJob> replay = service.start(execution, BackgroundJobType.MOVE_RECEIPT, 2);
      assertThat(replay).extracting(BackgroundJob::getId).containsExactly(id);
      assertThat(replay.getFirst()).isNotSameAs(firstAttempt.getFirst());
      if (checksumMatchesOnReplay) {
        JobExecutionResult replayResult = dispatcher.execute(replay.getFirst());
        receiptCalls.verify(receipts).hasReceiptChecksum(sourceId, checksum);
        receiptCalls.verify(receipts).moveTaxesFolderForJob(sourceId, destinationYear);
        service.finish(id, execution, JobExecutionOutcome.succeeded(replayResult));
      } else {
        // Negative control: a changed remote checksum must block the second move.
        assertThatThrownBy(() -> dispatcher.execute(replay.getFirst()))
            .isInstanceOfSatisfying(
                JobExecutionException.class,
                failure -> {
                  assertThat(failure.getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW);
                  service.finish(id, execution, JobExecutionOutcome.failed(failure));
                });
        receiptCalls.verify(receipts).hasReceiptChecksum(sourceId, checksum);
      }
      receiptCalls.verifyNoMoreInteractions();
      verifyNoMoreInteractions(receipts);
      BackgroundJob committed = load(id);
      assertThat(committed.getExecutionId()).isEqualTo(execution);
      assertThat(committed.isExecutionStarted()).isTrue();
      assertThat(committed.getAttempts()).isEqualTo(2);
      assertThat(committed.getTargetYear()).isEqualTo(destinationYear);
      assertThat(committed.getInboxItem().getLegacySourceId()).isEqualTo(sourceId);
      assertThat(committed.getState())
          .isEqualTo(
              checksumMatchesOnReplay ? BackgroundJobState.COMPLETED : BackgroundJobState.TERMINAL);
      assertThat(committed.getInboxItem().getExternalSyncState())
          .isEqualTo(
              checksumMatchesOnReplay
                  ? ExternalSyncState.SUCCEEDED
                  : ExternalSyncState.MANUAL_REVIEW);
      assertThat(committed.getTerminalReason())
          .isEqualTo(checksumMatchesOnReplay ? null : "MANUAL_REVIEW");
      assertThat(service.start(execution, BackgroundJobType.MOVE_RECEIPT, 3)).isEmpty();
    }

    @Test
    void realWorkerPersistsMixedBatchOutcomesThenSkipsSuccessAndTerminalMembers() {
      Long success = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 0, 5);
      Long retry = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 0, 5);
      Long terminal = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 0, 5);
      service.bindDue(10, Set.of(BackgroundJobType.TRANSLATE_OUTLOOK_ID));
      UUID id = load(success).getExecutionId();
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.executeBatch(anyList()))
          .thenReturn(
              Map.of(
                  success,
                      JobExecutionOutcome.succeeded(
                          JobExecutionResult.withImmutableSourceId("stable-id")),
                  retry, JobExecutionOutcome.failed(JobExecutionException.retryable("429", null)),
                  terminal,
                      JobExecutionOutcome.failed(
                          JobExecutionException.manualReview("ambiguous identity", null))));
      when(dispatcher.execute(any()))
          .thenReturn(JobExecutionResult.withImmutableSourceId("retry-id"));
      var raw = mock(org.jobrunr.storage.StorageProvider.class);
      var context = mock(org.jobrunr.jobs.context.JobContext.class);
      when(context.getJobId()).thenReturn(id);
      when(context.getJobState()).thenReturn(org.jobrunr.jobs.states.StateName.PROCESSING);
      var details =
          new JobDetails(
              DurableBackgroundJobWorker.class.getName(),
              null,
              "executeV1",
              List.of(
                  new org.jobrunr.jobs.JobParameter(String.class, "TRANSLATE_OUTLOOK_ID"),
                  org.jobrunr.jobs.JobParameter.JobContext));
      when(raw.getJobById(id))
          .thenReturn(
              new Job(
                  id,
                  details,
                  new org.jobrunr.jobs.states.ProcessingState(UUID.randomUUID(), "fixture")));
      var worker =
          new DurableBackgroundJobWorker(
              service,
              dispatcher,
              mock(org.jobrunr.scheduling.JobScheduler.class),
              raw,
              DurableBackgroundJobWorker.Settings.builder()
                  .enabled(true)
                  .allowedTypes(Set.of(BackgroundJobType.TRANSLATE_OUTLOOK_ID))
                  .initialDelayMillis(0)
                  .pollIntervalMillis(5000)
                  .maxJobsPerPoll(10)
                  .heartbeatTimeoutMultiplier(4)
                  .build(),
              java.time.Clock.systemUTC());
      worker.ready();
      assertThatThrownBy(() -> worker.executeV1("TRANSLATE_OUTLOOK_ID", context))
          .isInstanceOf(JobExecutionException.class);
      assertThat(load(success).getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(load(retry).getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(load(terminal).getTerminalReason()).isEqualTo("MANUAL_REVIEW");
      when(context.amountOfFailures()).thenReturn(1);
      worker.executeV1("TRANSLATE_OUTLOOK_ID", context);
      verify(dispatcher).execute(argThat(job -> job.getId().equals(retry)));
      verify(dispatcher).executeBatch(anyList());
      assertThat(load(success).getAttempts()).isEqualTo(1);
      assertThat(load(terminal).getAttempts()).isEqualTo(1);
      assertThat(load(retry).getAttempts()).isEqualTo(2);
      assertThat(load(retry).getState()).isEqualTo(BackgroundJobState.COMPLETED);
    }

    @Test
    void failedOutcomeCommitRollsBackWithoutLosingStartEvidenceAndCanBeRetried() {
      Long id = create(BackgroundJobType.MOVE_RECEIPT, 0, 5);
      service.bindDue(1, Set.of(BackgroundJobType.MOVE_RECEIPT));
      UUID execution = load(id).getExecutionId();
      service.start(execution, BackgroundJobType.MOVE_RECEIPT, 1);
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        service.finish(
                            id,
                            execution,
                            JobExecutionOutcome.succeeded(JobExecutionResult.completed()));
                        throw new IllegalStateException("simulated outcome commit failure");
                      }))
          .hasMessage("simulated outcome commit failure");
      assertThat(load(id).isExecutionStarted()).isTrue();
      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(service.start(execution, BackgroundJobType.MOVE_RECEIPT, 2)).hasSize(1);
      service.finish(id, execution, JobExecutionOutcome.succeeded(JobExecutionResult.completed()));
      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(load(id).getAttempts()).isEqualTo(2);
    }

    @Test
    void staleProviderProposalRollsBackAfterCommittedGenerationReset() throws Exception {
      var record =
          creation
              .findOrCreate(
                  InboxItemCreationService.IntakeSource.of(
                      "stale-parse", ExpenseSource.RECEIPT, "fixture"))
              .item();
      service.bindDue(10, Set.of(BackgroundJobType.PARSE_RECEIPT));
      UUID old = service.activeExecutionIds().getFirst();
      Long id = service.binding(old).getFirst().getId();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
      when(dispatcher.execute(any()))
          .thenAnswer(
              invocation -> {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                creation.markReady(
                    record.getId(),
                    EmailSuggestion.builder()
                        .emailType(EmailType.EXPENSE)
                        .amount(999.0)
                        .date("2026-09-01")
                        .description("stale proposal")
                        .category("OTHER")
                        .build(),
                    List.of());
                return JobExecutionResult.completed();
              });
      org.jobrunr.storage.StorageProvider raw = mock(org.jobrunr.storage.StorageProvider.class);
      var details =
          new JobDetails(
              DurableBackgroundJobWorker.class.getName(),
              null,
              "executeV1",
              List.of(
                  new org.jobrunr.jobs.JobParameter(String.class, "PARSE_RECEIPT"),
                  org.jobrunr.jobs.JobParameter.JobContext));
      when(raw.getJobById(old))
          .thenReturn(
              new Job(
                  old,
                  details,
                  new org.jobrunr.jobs.states.ProcessingState(UUID.randomUUID(), "fixture")));
      var context = mock(org.jobrunr.jobs.context.JobContext.class);
      when(context.getJobId()).thenReturn(old);
      when(context.getJobState()).thenReturn(org.jobrunr.jobs.states.StateName.PROCESSING);
      var worker =
          new DurableBackgroundJobWorker(
              service,
              dispatcher,
              mock(org.jobrunr.scheduling.JobScheduler.class),
              raw,
              DurableBackgroundJobWorker.Settings.builder()
                  .enabled(true)
                  .allowedTypes(Set.of(BackgroundJobType.PARSE_RECEIPT))
                  .initialDelayMillis(0)
                  .pollIntervalMillis(5000)
                  .maxJobsPerPoll(10)
                  .heartbeatTimeoutMultiplier(4)
                  .build(),
              java.time.Clock.systemUTC());
      worker.ready();
      try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
        Future<?> callback = executor.submit(() -> worker.executeV1("PARSE_RECEIPT", context));
        try {
          assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
          assertThat(load(id).isExecutionStarted()).isTrue();
          service.finish(
              id,
              old,
              JobExecutionOutcome.failed(
                  JobExecutionException.terminal("previous execution ended", null)));
          service.retry(id);
          service.bindDue(10, Set.of(BackgroundJobType.PARSE_RECEIPT));
        } finally {
          release.countDown();
        }
        callback.get(10, TimeUnit.SECONDS);
      }
      BackgroundJob current = load(id);
      assertThat(current.getExecutionId()).isNotEqualTo(old);
      assertThat(current.isExecutionStarted()).isFalse();
      assertThat(current.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(current.getInboxItem().getState()).isEqualTo(InboxState.QUEUED);
      assertThat(current.getInboxItem().getProposedAmount()).isNull();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions", Long.class))
          .isZero();
    }

    @Test
    void concurrentBindingCommitsOneGenerationAndNeverAddsLaterMembers() throws Exception {
      Long first = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 2, 5);
      Long second = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 0, 5);
      CountDownLatch go = new CountDownLatch(1);
      try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        Callable<Void> bind =
            () -> {
              assertThat(go.await(5, TimeUnit.SECONDS)).isTrue();
              service.bindDue(10, Set.of(BackgroundJobType.TRANSLATE_OUTLOOK_ID));
              return null;
            };
        Future<Void> a = executor.submit(bind);
        Future<Void> b = executor.submit(bind);
        go.countDown();
        a.get(10, TimeUnit.SECONDS);
        b.get(10, TimeUnit.SECONDS);
      }
      UUID generation = load(first).getExecutionId();
      assertThat(generation).isNotNull().isEqualTo(load(second).getExecutionId());
      assertThat(load(first).getExecutionAttemptBase()).isEqualTo(2);
      assertThat(load(first).getMaxAttempts()).isEqualTo(13);
      Long later = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 0, 5);
      service.bindDue(10, Set.of(BackgroundJobType.TRANSLATE_OUTLOOK_ID));
      assertThat(load(later).getExecutionId()).isNotEqualTo(generation);
      assertThat(service.binding(generation))
          .extracting(BackgroundJob::getId)
          .containsExactly(first, second);
    }

    @Test
    void bindingRollbackLeavesNoGenerationAndExhaustedLegacyWorkIsNotAdopted() {
      Long id = create(BackgroundJobType.MOVE_RECEIPT, 2, 5);
      Long exhausted = create(BackgroundJobType.MOVE_RECEIPT, 5, 5);
      tx.executeWithoutResult(
          status -> {
            service.bindDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT));
            assertThat(load(id).getExecutionId()).isNotNull();
            status.setRollbackOnly();
          });
      assertThat(load(id).getExecutionId()).isNull();
      assertThat(load(id).getMaxAttempts()).isEqualTo(5);
      service.bindDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT));
      assertThat(load(exhausted).getExecutionId()).isNull();
      assertThat(load(exhausted).getTerminalReason()).isEqualTo("MAX_ATTEMPTS");
      UUID generation = load(id).getExecutionId();
      service.bindDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT));
      assertThat(load(id).getExecutionId()).isEqualTo(generation);
    }

    @Test
    void staleCompletionAndProjectionCannotOverwriteCommittedResetOrDismissal() {
      Long id = create(BackgroundJobType.MOVE_RECEIPT, 0, 5);
      service.bindDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT));
      BackgroundJob bound = load(id);
      UUID old = bound.getExecutionId();
      service.start(old, bound.getType(), 1);
      service.finish(
          id, old, JobExecutionOutcome.failed(JobExecutionException.terminal("fixture", null)));
      service.retry(id);
      service.bindDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT));
      UUID current = load(id).getExecutionId();
      assertThat(current).isNotEqualTo(old);
      assertThat(
              service.finish(
                  id, old, JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .isFalse();
      service.project(old, null, Map.of(id, bound.getVersion()));
      assertThat(load(id).getExecutionId()).isEqualTo(current);
      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      tx.executeWithoutResult(
          status -> {
            BackgroundJob job = store.findForUpdate(id).orElseThrow();
            job.getInboxItem().setState(InboxState.DISMISSED);
            items.save(job.getInboxItem());
            store.terminalizeActiveForInbox(
                job.getInboxItem().getId(), "DISMISSED", LocalDateTime.now());
          });
      assertThat(service.start(current, bound.getType(), 2)).isEmpty();
      assertThat(
              service.finish(
                  id, current, JobExecutionOutcome.succeeded(JobExecutionResult.completed())))
          .isFalse();
      assertThat(load(id).getTerminalReason()).isEqualTo("DISMISSED");
      assertThatThrownBy(() -> service.retry(id))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void startAndOutcomeWindowsPreserveEvidenceAndSkipFinishedBatchMembers() {
      Long first = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 2, 5);
      Long second = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 0, 5);
      service.bindDue(10, Set.of(BackgroundJobType.TRANSLATE_OUTLOOK_ID));
      UUID id = load(first).getExecutionId();
      service.project(
          id, null, Map.of(first, load(first).getVersion(), second, load(second).getVersion()));
      assertThat(load(first).getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(service.start(id, BackgroundJobType.TRANSLATE_OUTLOOK_ID, 1)).hasSize(2);
      assertThat(load(first).isExecutionStarted()).isTrue();
      service.finish(
          first,
          id,
          JobExecutionOutcome.succeeded(JobExecutionResult.withImmutableSourceId("stable")));
      service.finish(
          second, id, JobExecutionOutcome.failed(JobExecutionException.retryable("429", null)));
      assertThat(service.start(id, BackgroundJobType.TRANSLATE_OUTLOOK_ID, 2))
          .extracting(BackgroundJob::getId)
          .containsExactly(second);
      assertThat(load(first).getAttempts()).isEqualTo(3);
      service.project(
          id, null, Map.of(first, load(first).getVersion(), second, load(second).getVersion()));
      assertThat(load(first).getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(load(second).getTerminalReason()).isEqualTo("MANUAL_REVIEW");
    }
  }

  @Nested
  class InboxLessReceiptMoves {

    @Test
    void aScheduledMoveRunsThroughARealGenerationAndCompletes() {
      Long id = tx.execute(status -> service.scheduleReceiptMove(41L, "drive-41", 2025).getId());
      assertThat(load(id).getInboxItem()).isNull();
      assertThat(load(id).getSourceExternalId()).isEqualTo("drive-41");

      BackgroundJob started = startGeneration(id);
      JobExecutionResult result = dispatcher.execute(started);
      service.finish(id, started.getExecutionId(), JobExecutionOutcome.succeeded(result));

      verify(receipts).moveTaxesFolderForJob("drive-41", 2025);
      verify(receipts, never()).hasReceiptChecksum(anyString(), anyString());
      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.COMPLETED);
    }

    @Test
    void aFailedMoveIsRequeuedWithItsErrorThenCompletesOnTheNextGeneration() {
      Long id = tx.execute(status -> service.scheduleReceiptMove(42L, "drive-42", 2025).getId());
      doThrow(
              IntegrationException.builder()
                  .kind(IntegrationFailureKind.TRANSIENT)
                  .message("OneDrive offline")
                  .build())
          .doNothing()
          .when(receipts)
          .moveTaxesFolderForJob("drive-42", 2025);

      BackgroundJob first = startGeneration(id);
      assertThatThrownBy(() -> dispatcher.execute(first))
          .isInstanceOfSatisfying(
              JobExecutionException.class,
              failure -> {
                assertThat(failure.getKind())
                    .isEqualTo(JobExecutionException.FailureKind.RETRYABLE);
                assertThat(
                        service.finish(
                            id, first.getExecutionId(), JobExecutionOutcome.failed(failure)))
                    .isTrue();
              });
      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(load(id).getLastError()).isEqualTo("OneDrive offline");

      List<BackgroundJob> second =
          service.start(first.getExecutionId(), BackgroundJobType.MOVE_RECEIPT, 2);
      service.finish(
          id,
          first.getExecutionId(),
          JobExecutionOutcome.succeeded(dispatcher.execute(second.getFirst())));

      verify(receipts, times(2)).moveTaxesFolderForJob("drive-42", 2025);
      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(load(id).getAttempts()).isEqualTo(2);
    }

    @Test
    void aTerminalMoveCanBeRetriedIntoAFreshGeneration() {
      Long id = tx.execute(status -> service.scheduleReceiptMove(43L, "drive-43", 2025).getId());
      BackgroundJob started = startGeneration(id);
      service.finish(
          id,
          started.getExecutionId(),
          JobExecutionOutcome.failed(JobExecutionException.manualReview("Not found", null)));
      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.TERMINAL);

      service.retry(id);

      BackgroundJob retried = load(id);
      assertThat(retried.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(retried.getAttempts()).isZero();
      assertThat(retried.getExecutionId()).isNull();
      assertThat(retried.getLastError()).isNull();
    }

    @Test
    void schedulingTheSameTransactionTwiceKeepsOneJob() {
      Long first = tx.execute(status -> service.scheduleReceiptMove(44L, "drive-44", 2025).getId());
      Long second =
          tx.execute(status -> service.scheduleReceiptMove(44L, "drive-44", 2025).getId());

      assertThat(second).isEqualTo(first);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM background_jobs WHERE source_external_id = 'drive-44'",
                  Long.class))
          .isOne();
    }

    @Test
    void theSchemaRejectsAJobWithBothOrNeitherSubject() {
      Long inboxJob = create(BackgroundJobType.MOVE_RECEIPT, 0, 5);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE background_jobs SET source_external_id = 'x' WHERE id = ?", inboxJob))
          .hasMessageContaining("CK_BACKGROUND_JOBS_SUBJECT");
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE background_jobs SET inbox_item_id = NULL WHERE id = ?", inboxJob))
          .hasMessageContaining("CK_BACKGROUND_JOBS_SUBJECT");
    }

    private BackgroundJob startGeneration(Long id) {
      tx.executeWithoutResult(
          status -> {
            BackgroundJob job = store.findForUpdate(id).orElseThrow();
            job.setAvailableAt(LocalDateTime.now().minusMinutes(1));
            store.save(job);
          });
      service.bindDue(1, Set.of(BackgroundJobType.MOVE_RECEIPT));
      UUID execution = load(id).getExecutionId();
      assertThat(execution).isNotNull();
      List<BackgroundJob> started = service.start(execution, BackgroundJobType.MOVE_RECEIPT, 1);
      assertThat(started).extracting(BackgroundJob::getId).containsExactly(id);
      return started.getFirst();
    }
  }

  @Nested
  class DismissedItems {

    @Test
    void refusedDismissedRowsNoLongerStarveTheRelay() {
      List<Long> refused = new ArrayList<>();
      for (int minutes = 30; minutes > 19; minutes--) {
        refused.add(createOutlook(BackgroundJobType.PARSE_OUTLOOK, InboxState.DISMISSED, minutes));
      }
      Long parse = createOutlook(BackgroundJobType.PARSE_OUTLOOK, InboxState.QUEUED, 1);

      service.bindDue(10, Set.of(BackgroundJobType.values()));
      service.bindDue(10, Set.of(BackgroundJobType.values()));

      assertThat(load(parse).getExecutionId()).isNotNull();
      assertThat(refused)
          .allSatisfy(
              id -> {
                BackgroundJob job = load(id);
                assertThat(job.getState()).isEqualTo(BackgroundJobState.TERMINAL);
                assertThat(job.getTerminalReason()).isEqualTo("DISMISSED");
                assertThat(job.getExecutionId()).isNull();
                assertThat(inboxState(id)).isEqualTo(InboxState.DISMISSED);
              });
    }

    @Test
    void dismissedOutlookMoveIsBoundStartedAndCompleted() {
      Long id = createOutlook(BackgroundJobType.MOVE_OUTLOOK, InboxState.DISMISSED, 1);

      service.bindDue(10, Set.of(BackgroundJobType.values()));
      UUID execution = load(id).getExecutionId();
      assertThat(execution).isNotNull();
      assertThat(service.start(execution, BackgroundJobType.MOVE_OUTLOOK, 1))
          .extracting(BackgroundJob::getId)
          .containsExactly(id);
      service.finish(id, execution, JobExecutionOutcome.succeeded(JobExecutionResult.completed()));

      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(inboxState(id)).isEqualTo(InboxState.DISMISSED);
      ExternalSyncState sync =
          tx.execute(
              status -> store.findById(id).orElseThrow().getInboxItem().getExternalSyncState());
      assertThat(sync).isEqualTo(ExternalSyncState.SUCCEEDED);
    }

    private Long createOutlook(BackgroundJobType type, InboxState state, int minutesAgo) {
      return tx.execute(
          status -> {
            InboxItem item =
                items.save(
                    InboxItem.builder()
                        .origin(ExpenseSource.OUTLOOK_EMAIL)
                        .state(state)
                        .externalSyncState(ExternalSyncState.PENDING)
                        .rawStatus(state.name())
                        .build());
            return store
                .save(
                    BackgroundJob.builder()
                        .inboxItem(item)
                        .type(type)
                        .idempotencyKey(UUID.randomUUID().toString())
                        .state(BackgroundJobState.AVAILABLE)
                        .attempts(0)
                        .maxAttempts(11)
                        .availableAt(LocalDateTime.now().minusMinutes(minutesAgo))
                        .build())
                .getId();
          });
    }

    private InboxState inboxState(Long jobId) {
      InboxState state =
          tx.execute(status -> store.findById(jobId).orElseThrow().getInboxItem().getState());
      return state;
    }
  }

  Long create(BackgroundJobType type, int attempts, int maximum) {
    return tx.execute(
        status -> {
          InboxItem item =
              items.save(
                  InboxItem.builder()
                      .origin(ExpenseSource.RECEIPT)
                      .state(InboxState.SAVED)
                      .externalSyncState(ExternalSyncState.PENDING)
                      .rawStatus("READY")
                      .build());
          return store
              .save(
                  BackgroundJob.builder()
                      .inboxItem(item)
                      .type(type)
                      .idempotencyKey(UUID.randomUUID().toString())
                      .state(BackgroundJobState.AVAILABLE)
                      .attempts(attempts)
                      .maxAttempts(maximum)
                      .availableAt(LocalDateTime.now().minusMinutes(1))
                      .build())
              .getId();
        });
  }

  BackgroundJob load(Long id) {
    return tx.execute(status -> store.findById(id).orElseThrow());
  }
}
