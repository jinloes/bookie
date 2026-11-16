package com.bookie.intake.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.BackgroundJobStore;
import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.application.IntakeJobDispatcher;
import com.bookie.intake.application.JobExecutionException;
import com.bookie.intake.application.JobExecutionOutcome;
import com.bookie.intake.application.JobExecutionResult;
import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.intake.sources.InboxItemCreationService;
import com.bookie.intake.sources.ReceiptService;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.ledger.domain.ExpenseSource;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

  private static final Duration LEASE = Duration.ofSeconds(300);

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
    tx.executeWithoutResult(status -> {
      jdbc.update("DELETE FROM background_jobs");
      jdbc.update("DELETE FROM inbox_artifacts");
      jdbc.update("DELETE FROM inbox_items");
    });
  }

  @Nested
  class LeasesAndReplay {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void receiptReplayAfterOutcomeRollbackRetainsIdentityYearAndValidatesChecksum(
        boolean checksumMatchesOnReplay) {
      String sourceId = "receipt-replay-item";
      String checksum = "a".repeat(64);
      int destinationYear = 2024;
      Long id = create(BackgroundJobType.MOVE_RECEIPT, 0, 5);
      tx.executeWithoutResult(status -> {
        BackgroundJob job = store.findForUpdate(id).orElseThrow();
        job.setTargetYear(destinationYear);
        job.getInboxItem().setSourceId(sourceId);
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
          service.claimDue(1, Set.of(BackgroundJobType.MOVE_RECEIPT), LEASE);
      assertThat(firstAttempt).extracting(BackgroundJob::getId).containsExactly(id);
      String firstLease = firstAttempt.getFirst().getLeaseOwner();
      JobExecutionResult firstResult = dispatcher.execute(firstAttempt.getFirst());
      receiptCalls.verify(receipts).hasReceiptChecksum(sourceId, checksum);
      receiptCalls.verify(receipts).moveTaxesFolderForJob(sourceId, destinationYear);
      List<Integer> completionStatuses = new ArrayList<>();

      assertThatThrownBy(() ->
              tx.executeWithoutResult(status -> {
                service.finish(id, firstLease, JobExecutionOutcome.succeeded(firstResult));
                entityManager.flush();
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
          .isInstanceOf(DataAccessResourceFailureException.class);
      assertThat(completionStatuses).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);

      BackgroundJob unfinished = load(id);
      assertThat(unfinished.getState()).isEqualTo(BackgroundJobState.LEASED);
      assertThat(unfinished.getAttempts()).isEqualTo(1);
      assertThat(unfinished.getTargetYear()).isEqualTo(destinationYear);
      assertThat(unfinished.getInboxItem().getArtifacts())
          .extracting(
              InboxArtifact::getType, InboxArtifact::getExternalId, InboxArtifact::getSha256)
          .containsExactly(tuple(InboxArtifactType.RECEIPT, sourceId, checksum));

      tx.executeWithoutResult(status -> {
        BackgroundJob job = store.findForUpdate(id).orElseThrow();
        job.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
        store.save(job);
      });
      List<BackgroundJob> replay =
          service.claimDue(1, Set.of(BackgroundJobType.MOVE_RECEIPT), LEASE);
      assertThat(replay).extracting(BackgroundJob::getId).containsExactly(id);
      String secondLease = replay.getFirst().getLeaseOwner();
      assertThat(secondLease).isNotEqualTo(firstLease);

      if (checksumMatchesOnReplay) {
        JobExecutionResult replayResult = dispatcher.execute(replay.getFirst());
        receiptCalls.verify(receipts).hasReceiptChecksum(sourceId, checksum);
        receiptCalls.verify(receipts).moveTaxesFolderForJob(sourceId, destinationYear);
        service.finish(id, secondLease, JobExecutionOutcome.succeeded(replayResult));
      } else {
        assertThatThrownBy(() -> dispatcher.execute(replay.getFirst()))
            .isInstanceOfSatisfying(
                JobExecutionException.class,
                failure -> {
                  assertThat(failure.getKind())
                      .isEqualTo(JobExecutionException.FailureKind.MANUAL_REVIEW);
                  service.finish(id, secondLease, JobExecutionOutcome.failed(failure));
                });
        receiptCalls.verify(receipts).hasReceiptChecksum(sourceId, checksum);
      }
      receiptCalls.verifyNoMoreInteractions();
      verifyNoMoreInteractions(receipts);
      BackgroundJob committed = load(id);
      assertThat(committed.getAttempts()).isEqualTo(2);
      assertThat(committed.getState())
          .isEqualTo(
              checksumMatchesOnReplay ? BackgroundJobState.COMPLETED : BackgroundJobState.TERMINAL);
    }

    @Test
    void realWorkerPersistsMixedBatchOutcomesThenRetriesOnlyRetryableMember() {
      Long success = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 0, 5);
      Long retry = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 0, 5);
      Long terminal = create(BackgroundJobType.TRANSLATE_OUTLOOK_ID, 0, 5);
      IntakeJobDispatcher mockDispatcher = mock(IntakeJobDispatcher.class);
      when(mockDispatcher.executeBatch(anyList()))
          .thenReturn(
              Map.of(
                  success,
                      JobExecutionOutcome.succeeded(
                          JobExecutionResult.withImmutableSourceId("stable-id")),
                  retry, JobExecutionOutcome.failed(JobExecutionException.retryable("429", null)),
                  terminal,
                      JobExecutionOutcome.failed(
                          JobExecutionException.manualReview("ambiguous identity", null))));
      when(mockDispatcher.execute(any()))
          .thenReturn(JobExecutionResult.withImmutableSourceId("retry-id"));
      var worker =
          new DurableBackgroundJobWorker(
              service,
              mockDispatcher,
              DurableBackgroundJobWorker.Settings.builder()
                  .enabled(true)
                  .allowedTypes(Set.of(BackgroundJobType.TRANSLATE_OUTLOOK_ID))
                  .initialDelayMillis(0)
                  .pollIntervalMillis(5000)
                  .maxJobsPerPoll(10)
                  .leaseSeconds(300)
                  .build(),
              Clock.systemUTC(),
              () -> false);
      worker.ready();

      worker.relayNow();

      assertThat(load(success).getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(load(retry).getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(load(terminal).getTerminalReason()).isEqualTo("MANUAL_REVIEW");

      tx.executeWithoutResult(status -> {
        BackgroundJob job = store.findForUpdate(retry).orElseThrow();
        job.setAvailableAt(LocalDateTime.now().minusSeconds(1));
        store.save(job);
      });
      worker.relayNow();

      verify(mockDispatcher).execute(argThat(job -> job.getId().equals(retry)));
      verify(mockDispatcher).executeBatch(anyList());
      assertThat(load(success).getAttempts()).isEqualTo(1);
      assertThat(load(terminal).getAttempts()).isEqualTo(1);
      assertThat(load(retry).getAttempts()).isEqualTo(2);
      assertThat(load(retry).getState()).isEqualTo(BackgroundJobState.COMPLETED);
    }

    @Test
    void staleProviderProposalRollsBackAfterCommittedGenerationReset() throws Exception {
      var record =
          creation
              .findOrCreate(
                  InboxItemCreationService.IntakeSource.of(
                      "stale-parse", ExpenseSource.RECEIPT, "fixture"))
              .item();
      Long id = service.findByInboxItemId(record.getId()).getFirst().getId();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      IntakeJobDispatcher mockDispatcher = mock(IntakeJobDispatcher.class);
      when(mockDispatcher.execute(any()))
          .thenAnswer(invocation -> {
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
      var worker =
          new DurableBackgroundJobWorker(
              service,
              mockDispatcher,
              DurableBackgroundJobWorker.Settings.builder()
                  .enabled(true)
                  .allowedTypes(Set.of(BackgroundJobType.PARSE_RECEIPT))
                  .initialDelayMillis(0)
                  .pollIntervalMillis(5000)
                  .maxJobsPerPoll(10)
                  .leaseSeconds(300)
                  .build(),
              Clock.systemUTC(),
              () -> false);
      worker.ready();

      try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
        Future<?> callback = executor.submit(worker::relayNow);
        try {
          assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
          String oldLease = load(id).getLeaseOwner();
          service.finish(
              id,
              oldLease,
              JobExecutionOutcome.failed(
                  JobExecutionException.terminal("previous execution ended", null)));
          service.retry(id);
        } finally {
          release.countDown();
        }
        callback.get(10, TimeUnit.SECONDS);
      }

      BackgroundJob current = load(id);
      assertThat(current.getLeaseOwner()).isNull();
      assertThat(current.getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(current.getInboxItem().getState()).isEqualTo(InboxState.QUEUED);
      assertThat(current.getInboxItem().getProposedAmount()).isNull();
    }

    @Test
    void concurrentClaimsNeverDeliverTheSameJobTwice() throws Exception {
      Long first = create(BackgroundJobType.MOVE_RECEIPT, 0, 5);
      Long second = create(BackgroundJobType.MOVE_RECEIPT, 0, 5);
      CountDownLatch go = new CountDownLatch(1);
      try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        Callable<List<Long>> claim =
            () -> {
              assertThat(go.await(5, TimeUnit.SECONDS)).isTrue();
              return service.claimDue(10, Set.of(BackgroundJobType.MOVE_RECEIPT), LEASE).stream()
                  .map(BackgroundJob::getId)
                  .toList();
            };
        Future<List<Long>> a = executor.submit(claim);
        Future<List<Long>> b = executor.submit(claim);
        go.countDown();
        List<Long> combined = new ArrayList<>(a.get(10, TimeUnit.SECONDS));
        combined.addAll(b.get(10, TimeUnit.SECONDS));
        assertThat(combined).containsExactlyInAnyOrder(first, second);
      }
    }
  }

  @Nested
  class InboxLessReceiptMoves {

    @Test
    void aScheduledMoveRunsAndRetriesOnTransientError() {
      Long id = tx.execute(status -> service.scheduleReceiptMove(42L, "drive-42", 2025).getId());
      tx.executeWithoutResult(status -> {
        BackgroundJob job = store.findForUpdate(id).orElseThrow();
        job.setAvailableAt(LocalDateTime.now().minusMinutes(1));
        store.save(job);
      });
      doThrow(
              IntegrationException.builder()
                  .kind(IntegrationFailureKind.TRANSIENT)
                  .message("OneDrive offline")
                  .build())
          .doNothing()
          .when(receipts)
          .moveTaxesFolderForJob("drive-42", 2025);

      BackgroundJob first =
          service.claimDue(1, Set.of(BackgroundJobType.MOVE_RECEIPT), LEASE).getFirst();
      assertThatThrownBy(() -> dispatcher.execute(first))
          .isInstanceOfSatisfying(
              JobExecutionException.class,
              failure ->
                  assertThat(
                          service.finish(
                              id, first.getLeaseOwner(), JobExecutionOutcome.failed(failure)))
                      .isTrue());
      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.AVAILABLE);
      assertThat(load(id).getLastError()).isEqualTo("OneDrive offline");

      tx.executeWithoutResult(status -> {
        BackgroundJob job = store.findForUpdate(id).orElseThrow();
        job.setAvailableAt(LocalDateTime.now().minusSeconds(1));
        store.save(job);
      });
      BackgroundJob second =
          service.claimDue(1, Set.of(BackgroundJobType.MOVE_RECEIPT), LEASE).getFirst();
      service.finish(
          id, second.getLeaseOwner(), JobExecutionOutcome.succeeded(dispatcher.execute(second)));

      verify(receipts, times(2)).moveTaxesFolderForJob("drive-42", 2025);
      verify(receipts, never()).hasReceiptChecksum(anyString(), anyString());
      assertThat(load(id).getState()).isEqualTo(BackgroundJobState.COMPLETED);
      assertThat(load(id).getAttempts()).isEqualTo(2);
    }

    @Test
    void theSchemaRejectsAJobWithBothOrNeitherSubject() {
      Long inboxJob = create(BackgroundJobType.MOVE_RECEIPT, 0, 5);

      assertThatThrownBy(() ->
              jdbc.update(
                  "UPDATE background_jobs SET source_external_id = 'x' WHERE id = ?", inboxJob))
          .hasMessageContaining("CK_BACKGROUND_JOBS_SUBJECT");
      assertThatThrownBy(() ->
              jdbc.update("UPDATE background_jobs SET inbox_item_id = NULL WHERE id = ?", inboxJob))
          .hasMessageContaining("CK_BACKGROUND_JOBS_SUBJECT");
    }
  }

  @Nested
  class DismissedItems {

    @Test
    void refusedDismissedRowsAreTerminalizedAndDismissedOutlookMovesStillComplete() {
      Long refused = createOutlook(BackgroundJobType.PARSE_OUTLOOK, InboxState.DISMISSED);
      Long move = createOutlook(BackgroundJobType.MOVE_OUTLOOK, InboxState.DISMISSED);

      List<BackgroundJob> claimed = service.claimDue(10, Set.of(BackgroundJobType.values()), LEASE);

      assertThat(claimed).extracting(BackgroundJob::getId).containsExactly(move);
      assertThat(load(refused).getState()).isEqualTo(BackgroundJobState.TERMINAL);
      assertThat(load(refused).getTerminalReason()).isEqualTo("DISMISSED");

      service.finish(
          move,
          claimed.getFirst().getLeaseOwner(),
          JobExecutionOutcome.succeeded(JobExecutionResult.completed()));
      assertThat(load(move).getState()).isEqualTo(BackgroundJobState.COMPLETED);
      ExternalSyncState sync =
          tx.execute(
              status -> store.findById(move).orElseThrow().getInboxItem().getExternalSyncState());
      assertThat(sync).isEqualTo(ExternalSyncState.SUCCEEDED);
    }

    private Long createOutlook(BackgroundJobType type, InboxState state) {
      return tx.execute(status -> {
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
                    .availableAt(LocalDateTime.now().minusMinutes(1))
                    .build())
            .getId();
      });
    }
  }

  Long create(BackgroundJobType type, int attempts, int maximum) {
    return tx.execute(status -> {
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
