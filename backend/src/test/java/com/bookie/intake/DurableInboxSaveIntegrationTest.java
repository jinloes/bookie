package com.bookie.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.intake.application.*;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.intake.sources.InboxItemCreationService;
import com.bookie.integrations.onedrive.OneDrivePort;
import com.bookie.ledger.application.LedgerTransactionInput;
import com.bookie.ledger.domain.ExpenseSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

  @Autowired private InboxItemCreationService creation;
  @Autowired private InboxQueryService inboxQueryService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private BackgroundJobService jobs;
  @Autowired private InboxCommands inboxCommands;
  @Autowired private com.bookie.ledger.application.LedgerTransactionService ledgerTransactions;
  @MockitoSpyBean private DurableBackgroundJobWorker configuredWorker;
  // The worker is disabled, so any OneDrive call here would be an inline, pre-commit receipt move.
  @MockitoBean private OneDrivePort oneDrive;

  @Test
  void saveCommitsTheLedgerRowSavedStateAndDurableExternalJobTogether() {
    Long itemId = readyReceipt("receipt-item-001");

    InboxItem item = inboxCommands.save(itemId, request());

    assertThat(count("financial_transactions")).isEqualTo(1);
    assertThat(item.getState()).isEqualTo(InboxState.SAVED);
    assertThat(item.getExternalSyncState()).isEqualTo(ExternalSyncState.PENDING);
    assertThat(item.getFinancialTransactionId()).isNotNull();
    assertThat(item.getSourceId()).isEqualTo("receipt-item-001");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT external_id FROM transaction_import_references WHERE transaction_id = ?",
                String.class,
                item.getFinancialTransactionId()))
        .isEqualTo("receipt-item-001");
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
    assertThat(inboxQueryService.findActive()).extracting(InboxItem::getId).doesNotContain(itemId);
    verifyNoInteractions(oneDrive);
  }

  @Test
  void reviewCommitsProposalButNoFinancialRowsAndRollbackRemovesSaveAndIntent() {
    Long pending = readyReceipt("receipt-" + UUID.randomUUID());
    assertThat(count("financial_transactions")).isZero();
    assertThat(moveCount()).isZero();
    assertThat(inboxQueryService.findAll().getFirst().getState()).isEqualTo(InboxState.READY);
    assertThatThrownBy(() ->
            new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                  inboxCommands.save(pending, request());
                  assertThat(count("financial_transactions")).isEqualTo(1);
                  assertThat(moveCount()).isEqualTo(1);
                  verifyNoInteractions(oneDrive);
                  throw new IllegalStateException("force rollback after intent");
                }))
        .hasMessage("force rollback after intent");
    assertThat(count("financial_transactions")).isZero();
    assertThat(moveCount()).isZero();
    assertThat(inboxQueryService.findAll().getFirst().getState()).isEqualTo(InboxState.READY);
    verifyNoInteractions(oneDrive);
  }

  @Test
  void committedSaveSurvivesLostHintAndDirectPollerExecutesDurableJob() {
    InboxItem saved = inboxCommands.save(readyReceipt("receipt-" + UUID.randomUUID()), request());
    assertThat(saved.getFinancialTransactionId()).isNotNull();
    assertThat(count("financial_transactions")).isEqualTo(1);
    assertThat(moveCount()).isEqualTo(1);

    IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
    when(dispatcher.execute(any())).thenReturn(JobExecutionResult.completed());
    DurableBackgroundJobWorker poller =
        new DurableBackgroundJobWorker(
            jobs,
            dispatcher,
            DurableBackgroundJobWorker.Settings.builder()
                .enabled(true)
                .allowedTypes(Set.of(BackgroundJobType.MOVE_RECEIPT))
                .initialDelayMillis(0)
                .pollIntervalMillis(5000)
                .maxJobsPerPoll(10)
                .leaseSeconds(300)
                .build(),
            Clock.systemUTC(),
            () -> false);
    poller.ready();
    poller.relay();

    verify(dispatcher).execute(argThat(job -> job.getType() == BackgroundJobType.MOVE_RECEIPT));
    assertThat(
            inboxQueryService.findJobs(saved.getId()).stream()
                .filter(job -> job.getType() == BackgroundJobType.MOVE_RECEIPT)
                .findFirst()
                .orElseThrow()
                .getState())
        .isEqualTo(BackgroundJobState.COMPLETED);
    assertThat(count("financial_transactions")).isEqualTo(1);
    assertThat(moveCount()).isEqualTo(1);
  }

  @Test
  void failedAsynchronousHintCannotMisreportCommittedSaveAndRelayRecovers() throws Exception {
    IntakeJobDispatcher dispatcher = mock(IntakeJobDispatcher.class);
    when(dispatcher.execute(any())).thenReturn(JobExecutionResult.completed());
    var poller =
        new DurableBackgroundJobWorker(
            jobs,
            dispatcher,
            DurableBackgroundJobWorker.Settings.builder()
                .enabled(true)
                .allowedTypes(Set.of(BackgroundJobType.MOVE_RECEIPT))
                .initialDelayMillis(0)
                .pollIntervalMillis(5000)
                .maxJobsPerPoll(10)
                .leaseSeconds(300)
                .build(),
            Clock.systemUTC(),
            () -> false);
    poller.ready();

    Long ready = readyReceipt("receipt-" + UUID.randomUUID());
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> verify(configuredWorker).runAvailableJob(null));
    clearInvocations(configuredWorker);
    var attempted = new java.util.concurrent.CountDownLatch(1);
    var observed = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    doAnswer(
            invocation -> {
              IllegalStateException failure = new IllegalStateException("async hint failed");
              observed.set(failure);
              attempted.countDown();
              throw failure;
            })
        .when(configuredWorker)
        .runAvailableJob(any());

    InboxItem saved = inboxCommands.save(ready, request());
    assertThat(saved.getFinancialTransactionId()).isNotNull();
    assertThat(attempted.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    assertThat(observed.get()).hasMessage("async hint failed");
    assertThat(count("financial_transactions")).isEqualTo(1);
    assertThat(moveCount()).isEqualTo(1);

    poller.relay();
    assertThat(
            inboxQueryService.findJobs(saved.getId()).stream()
                .filter(job -> job.getType() == BackgroundJobType.MOVE_RECEIPT)
                .findFirst()
                .orElseThrow()
                .getState())
        .isEqualTo(BackgroundJobState.COMPLETED);
  }

  @Test
  void unifiedWritesAddNoRowsToTheRetiredLegacyTables() throws Exception {
    Long neutralExpense =
        jdbcTemplate.queryForObject(
            "SELECT id FROM neutral_categories WHERE category_key = 'OTHER_EXPENSE'", Long.class);
    Long neutralIncome =
        jdbcTemplate.queryForObject(
            "SELECT id FROM neutral_categories WHERE category_key = 'OTHER_INCOME'", Long.class);
    ledgerTransactions.create(
        LedgerTransactionInput.builder()
            .amount(new BigDecimal("12.00"))
            .direction(TransactionDirection.EXPENSE)
            .date(LocalDate.of(2026, 8, 24))
            .description("Unified-only expense")
            .activityId(activityId())
            .neutralCategoryId(neutralExpense)
            .build());
    VenmoImportResult imported =
        inboxCommands.importVenmo(
            VenmoImportCommand.builder()
                .content(
                    """
                    ,ID,Datetime,Type,Status,Note,From,To,Amount (total)
                    ,unified-venmo-1,2026-08-20T09:00:00,Payment,Complete,Rent,@payer,Me,"+ $10.00"
                    """
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .fileName("venmo.csv")
                .activityId(activityId())
                .build());
    assertThat(imported.importedRows()).isEqualTo(1);
    InboxItem venmo =
        inboxQueryService.findActive().stream()
            .filter(item -> "unified-venmo-1".equals(item.getSourceId()))
            .findFirst()
            .orElseThrow();
    inboxCommands.save(
        venmo.getId(),
        SaveInboxItemCommand.builder()
            .direction(TransactionDirection.INCOME)
            .amount(new BigDecimal("10.00"))
            .date(LocalDate.of(2026, 8, 20))
            .description("Rent")
            .activityId(activityId())
            .neutralCategoryId(neutralIncome)
            .build());

    assertThat(count("financial_transactions")).isEqualTo(2);
    assertThat(retiredRowCount("expenses")).isZero();
    assertThat(retiredRowCount("incomes")).isZero();
    assertThat(retiredRowCount("pending_expenses")).isZero();
    assertThat(retiredRowCount("pending_incomes")).isZero();
  }

  // Once V20 retires a legacy table it cannot hold rows; before that it must stay empty.
  private long retiredRowCount(String table) {
    Long present =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = UPPER(?)",
            Long.class,
            table);
    return present == null || present == 0 ? 0 : count(table);
  }

  private Long readyReceipt(String sourceId) {
    InboxItem item =
        creation
            .findOrCreate(
                InboxItemCreationService.IntakeSource.of(
                    sourceId, ExpenseSource.RECEIPT, "synthetic-receipt.pdf"))
            .item();
    creation.markReady(
        item.getId(),
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
    return item.getId();
  }

  private SaveInboxItemCommand request() {
    return SaveInboxItemCommand.builder()
        .direction(TransactionDirection.EXPENSE)
        .amount(new BigDecimal("91.25"))
        .description("Fixture")
        .date(LocalDate.of(2026, 8, 24))
        .activityId(activityId())
        .neutralCategoryId(neutralCategoryId("OTHER_EXPENSE"))
        .build();
  }

  private Long neutralCategoryId(String key) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM neutral_categories WHERE category_key = ?", Long.class, key);
  }

  private Long activityId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'",
        Long.class);
  }

  private Long categoryId() {
    return neutralCategoryId("OTHER_EXPENSE");
  }

  private long moveCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM background_jobs WHERE type = 'MOVE_RECEIPT'", Long.class);
  }

  private long count(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }
}
