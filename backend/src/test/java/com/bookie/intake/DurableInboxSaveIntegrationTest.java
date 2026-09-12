package com.bookie.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookie.intake.application.*;
import com.bookie.intake.application.InboxQueryService;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.SavePendingExpenseRequest;
import com.bookie.service.InboxSaveOrchestrator;
import com.bookie.service.PendingExpenseService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:durable_inbox_save_${random.uuid};DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=validate",
      "bookie.auto-import.enabled=false",
      "bookie.intake.worker.enabled=false"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DurableInboxSaveIntegrationTest {

  @Autowired private PendingExpenseService pendingExpenseService;
  @Autowired private InboxQueryService inboxQueryService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private InboxSaveOrchestrator orchestrator;
  @Autowired private BackgroundJobService jobs;
  @Autowired private StorageProvider raw;
  @MockitoSpyBean private JobScheduler scheduler;
  @MockitoSpyBean private DurableBackgroundJobWorker configuredWorker;

  @Test
  void saveCommitsLegacyAndUnifiedLedgerRowsWithDurableExternalJob() {
    Long activityId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'",
            Long.class);
    Long categoryId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'", Long.class);

    var pending =
        pendingExpenseService.create(
            "receipt-item-001", ExpenseSource.RECEIPT, "synthetic-receipt.pdf");
    pendingExpenseService.markReady(
        pending.getId(),
        EmailSuggestion.builder()
            .emailType(EmailType.EXPENSE)
            .amount(91.25)
            .description("Synthetic durable expense")
            .date("2026-08-24")
            .category("OTHER")
            .activityId(activityId)
            .categoryId(categoryId)
            .classificationAmbiguous(false)
            .build(),
        List.of("Synthetic Alias"));

    Expense saved =
        pendingExpenseService.saveAsExpense(
            pending.getId(),
            new SavePendingExpenseRequest(
                new BigDecimal("91.25"),
                "Synthetic durable expense",
                LocalDate.of(2026, 8, 24),
                "OTHER",
                null,
                null,
                activityId,
                categoryId));

    assertThat(count("pending_expenses")).isZero();
    assertThat(count("expenses")).isEqualTo(1);
    assertThat(count("financial_transactions")).isEqualTo(1);
    assertThat(count("legacy_transaction_map")).isEqualTo(1);

    InboxItem item = inboxQueryService.findAll().getFirst();
    assertThat(item.getState()).isEqualTo(InboxState.SAVED);
    assertThat(item.getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);
    assertThat(item.getFinancialTransactionId()).isNotNull();
    assertThat(item.getLegacySourceId()).isEqualTo("receipt-item-001");
    assertThat(saved.getSourceId()).isEqualTo("receipt-item-001");
    assertThat(inboxQueryService.findJobs(item.getId()))
        .extracting(job -> job.getType())
        .containsExactlyInAnyOrder(BackgroundJobType.PARSE_RECEIPT, BackgroundJobType.MOVE_RECEIPT);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT target_year
                FROM background_jobs
                WHERE type = 'MOVE_RECEIPT'
                """,
                Integer.class))
        .isEqualTo(2026);
  }

  @Test
  void reviewCommitsProposalButNoFinancialRowsAndRollbackRemovesSaveAndIntent() {
    Long pending = readyReceipt();
    assertThat(count("expenses")).isZero();
    assertThat(count("financial_transactions")).isZero();
    assertThat(moveCount()).isZero();
    assertThat(inboxQueryService.findAll().getFirst().getState()).isEqualTo(InboxState.READY);
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        status -> {
                          pendingExpenseService.saveAsExpense(pending, request());
                          assertThat(count("expenses")).isEqualTo(1);
                          assertThat(moveCount()).isEqualTo(1);
                          throw new IllegalStateException("force rollback after intent");
                        }))
        .hasMessage("force rollback after intent");
    assertThat(count("expenses")).isZero();
    assertThat(count("financial_transactions")).isZero();
    assertThat(count("legacy_transaction_map")).isZero();
    assertThat(moveCount()).isZero();
    assertThat(count("pending_expenses")).isEqualTo(1);
    assertThat(inboxQueryService.findAll().getFirst().getState()).isEqualTo(InboxState.READY);
  }

  @Test
  void committedSaveSurvivesLostHintAndAcceptedEnqueueWithLostResponse() {
    Expense expense = orchestrator.saveAsExpense(readyReceipt(), request());
    assertThat(expense.getId()).isNotNull();
    assertThat(count("expenses")).isEqualTo(1);
    assertThat(count("financial_transactions")).isEqualTo(1);
    assertThat(moveCount()).isEqualTo(1);
    assertThat(raw.getJobStats().getTotal()).isZero();
    IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
    DurableBackgroundJobWorker relay =
        new DurableBackgroundJobWorker(
            jobs,
            dispatcher,
            scheduler,
            raw,
            DurableBackgroundJobWorker.Settings.builder()
                .enabled(true)
                .allowedTypes(Set.of(BackgroundJobType.MOVE_RECEIPT))
                .initialDelayMillis(0)
                .pollIntervalMillis(5000)
                .maxJobsPerPoll(10)
                .heartbeatTimeoutMultiplier(4)
                .build(),
            Clock.systemUTC());
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("accepted but response lost");
            })
        .when(scheduler)
        .enqueue(any(UUID.class), any(IocJobLambda.class));
    relay.ready();
    assertThatThrownBy(relay::relay).hasMessage("accepted but response lost");
    UUID execution = jobs.activeExecutionIds().getFirst();
    assertThat(raw.getJobById(execution)).isNotNull();
    relay.relay();
    assertThat(jobs.activeExecutionIds()).containsExactly(execution);
    verify(scheduler).enqueue(eq(execution), any(IocJobLambda.class));
    verifyNoInteractions(dispatcher);
    assertThat(count("expenses")).isEqualTo(1);
    assertThat(moveCount()).isEqualTo(1);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void failedAsynchronousHintCannotMisreportCommittedSaveAndRelayRecovers(boolean accepted)
      throws Exception {
    IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
    var relay =
        new DurableBackgroundJobWorker(
            jobs,
            dispatcher,
            scheduler,
            raw,
            DurableBackgroundJobWorker.Settings.builder()
                .enabled(true)
                .allowedTypes(Set.of(BackgroundJobType.MOVE_RECEIPT))
                .initialDelayMillis(0)
                .pollIntervalMillis(5000)
                .maxJobsPerPoll(10)
                .heartbeatTimeoutMultiplier(4)
                .build(),
            Clock.systemUTC());
    relay.ready();
    doAnswer(
            invocation -> {
              if (accepted) {
                invocation.callRealMethod();
              }
              throw new IllegalStateException("publication response failed");
            })
        .when(scheduler)
        .enqueue(any(UUID.class), any(IocJobLambda.class));
    var attempted = new java.util.concurrent.CountDownLatch(1);
    var observed = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    doAnswer(
            invocation -> {
              try {
                relay.runAvailableForSource(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2));
              } catch (IllegalStateException failure) {
                observed.set(failure);
                throw failure;
              } finally {
                attempted.countDown();
              }
              return null;
            })
        .when(configuredWorker)
        .runAvailableForSource(any(), anyString(), any());
    Expense expense = orchestrator.saveAsExpense(readyReceipt(), request());
    assertThat(expense.getId()).isNotNull();
    assertThat(attempted.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    assertThat(observed.get()).hasMessage("publication response failed");
    assertThat(count("expenses")).isEqualTo(1);
    assertThat(count("financial_transactions")).isEqualTo(1);
    assertThat(moveCount()).isEqualTo(1);
    UUID id = jobs.activeExecutionIds().getFirst();
    assertThat(raw.getJobStats().getTotal()).isEqualTo(accepted ? 1 : 0);
    doCallRealMethod().when(scheduler).enqueue(any(UUID.class), any(IocJobLambda.class));
    relay.relay();
    relay.relay();
    assertThat(jobs.activeExecutionIds()).containsExactly(id);
    assertThat(raw.getJobStats().getTotal()).isEqualTo(1);
    assertThat(raw.getJobById(id)).isNotNull();
    verifyNoInteractions(dispatcher);
  }

  private Long readyReceipt() {
    var pending =
        pendingExpenseService.create(
            "receipt-" + UUID.randomUUID(), ExpenseSource.RECEIPT, "fixture.pdf");
    pendingExpenseService.markReady(
        pending.getId(),
        EmailSuggestion.builder()
            .emailType(EmailType.EXPENSE)
            .amount(91.25)
            .description("Fixture")
            .date("2026-08-24")
            .category("OTHER")
            .activityId(activityId())
            .categoryId(categoryId())
            .classificationAmbiguous(false)
            .build(),
        List.of());
    return pending.getId();
  }

  private SavePendingExpenseRequest request() {
    return new SavePendingExpenseRequest(
        new BigDecimal("91.25"),
        "Fixture",
        LocalDate.of(2026, 8, 24),
        "OTHER",
        null,
        null,
        activityId(),
        categoryId());
  }

  private Long activityId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'",
        Long.class);
  }

  private Long categoryId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'", Long.class);
  }

  private long moveCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM background_jobs WHERE type = 'MOVE_RECEIPT'", Long.class);
  }

  private long count(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }
}
