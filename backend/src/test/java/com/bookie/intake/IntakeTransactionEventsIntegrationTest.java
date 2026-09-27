package com.bookie.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.InboxCommands;
import com.bookie.intake.application.SaveInboxItemCommand;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.intake.sources.InboxItemCreationService;
import com.bookie.ledger.application.LedgerTransactionInput;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.domain.ExpenseSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pins how intake reacts to transactions: a job queued or saved inside a transaction is kicked
 * once, only after that transaction commits, and a manually entered receipt schedules its durable
 * MOVE_RECEIPT job inside the ledger's create transaction.
 */
// One context for the class, closed afterwards together with its in-memory database (no
// DB_CLOSE_DELAY), so it adds nothing to the suite's cached contexts; each test isolates itself by
// source identity instead of by a fresh database.
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:intake_transaction_events",
      "spring.jpa.hibernate.ddl-auto=validate",
      "bookie.auto-import.enabled=false",
      "bookie.intake.worker.enabled=false"
    })
@DirtiesContext
class IntakeTransactionEventsIntegrationTest {

  private static final Duration WAIT = Duration.ofSeconds(10);

  @Autowired private InboxItemCreationService creation;
  @Autowired private InboxCommands inboxCommands;
  @Autowired private LedgerTransactionService ledgerTransactions;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  // A plain mock rather than a spy: listener threads outlive the context and retain the last mock
  // they invoked, and a spy would pin every collaborator of the real worker. The configured worker
  // is disabled, so its real kick does nothing either.
  @MockitoBean private DurableBackgroundJobWorker worker;

  @Nested
  class InboxKickoffs {

    @Test
    void aSaveKicksTheRelayOnceOnlyAfterItsTransactionCommits() throws Exception {
      Long itemId = readyReceipt();
      CountDownLatch entered = new CountDownLatch(1);
      AtomicBoolean transactionActive = new AtomicBoolean(true);
      doAnswer(
              invocation -> {
                transactionActive.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
                entered.countDown();
                return null;
              })
          .when(worker)
          .runAvailableJob(any());

      transactionally(
          () -> {
            inboxCommands.save(itemId, saveCommand());
            assertThat(entered.getCount()).isOne();
          });

      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(transactionActive).isFalse();
      await()
          .during(Duration.ofMillis(500))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(worker, times(1)).runAvailableJob(null));
    }

    @Test
    void aRolledBackSaveDoesNotKick() {
      Long itemId = readyReceipt();

      assertThatThrownBy(
              () ->
                  transactionally(
                      () -> {
                        inboxCommands.save(itemId, saveCommand());
                        throw new IllegalStateException("force rollback");
                      }))
          .hasMessage("force rollback");

      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(worker, never()).runAvailableJob(any()));
    }

    @Test
    void aQueuedItemKicksItsParseAfterCommit() {
      creation.findOrCreate(
          InboxItemCreationService.IntakeSource.of(
              "receipt-" + UUID.randomUUID(), ExpenseSource.RECEIPT, "queued.pdf"));

      await().atMost(WAIT).untilAsserted(() -> verify(worker).runAvailableJob(null));
    }
  }

  @Nested
  class ReceiptFiling {

    @Test
    void aReceiptCreateSchedulesItsMoveInTheCreateTransactionAndKicksAfterCommit() {
      String driveItemId = "drive-" + UUID.randomUUID();
      transactionally(
          () -> {
            ledgerTransactions.create(receiptInput("RECEIPT", driveItemId, 2026));
            assertThat(moveJobs(driveItemId))
                .singleElement()
                .satisfies(
                    job -> {
                      assertThat(job.get("TYPE")).isEqualTo("MOVE_RECEIPT");
                      assertThat(job.get("STATE")).isEqualTo("AVAILABLE");
                      assertThat(job.get("TARGET_YEAR")).isEqualTo(2026);
                      assertThat(job.get("INBOX_ITEM_ID")).isNull();
                    });
            verify(worker, never()).runAvailableJob(any());
          });

      await().atMost(WAIT).untilAsserted(() -> verify(worker).runAvailableJob(null));
      assertThat(moveJobs(driveItemId)).hasSize(1);
    }

    @Test
    void aNonReceiptCreateSchedulesNothing() {
      String driveItemId = "drive-" + UUID.randomUUID();

      ledgerTransactions.create(receiptInput("MANUAL", driveItemId, 2026));

      assertThat(transactionsFor(driveItemId)).isOne();
      assertThat(moveJobs(driveItemId)).isEmpty();
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(worker, never()).runAvailableJob(any()));
    }

    @Test
    void aRolledBackReceiptCreateLeavesNoTransactionNoJobAndNoKick() {
      String driveItemId = "drive-" + UUID.randomUUID();

      assertThatThrownBy(
              () ->
                  transactionally(
                      () -> {
                        ledgerTransactions.create(receiptInput("RECEIPT", driveItemId, 2026));
                        assertThat(moveJobs(driveItemId)).hasSize(1);
                        throw new IllegalStateException("force rollback");
                      }))
          .hasMessage("force rollback");

      assertThat(transactionsFor(driveItemId)).isZero();
      assertThat(moveJobs(driveItemId)).isEmpty();
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(worker, never()).runAvailableJob(any()));
    }
  }

  private void transactionally(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
  }

  private Long readyReceipt() {
    InboxItem item =
        creation
            .findOrCreate(
                InboxItemCreationService.IntakeSource.of(
                    "receipt-" + UUID.randomUUID(), ExpenseSource.RECEIPT, "synthetic.pdf"))
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
            .categoryId(
                jdbcTemplate.queryForObject(
                    "SELECT id FROM financial_categories WHERE category_key = 'OTHER_EXPENSE'",
                    Long.class))
            .classificationAmbiguous(false)
            .build(),
        List.of());
    // Let the item's parse kick finish so each test observes only its own kicks.
    await().atMost(WAIT).untilAsserted(() -> verify(worker).runAvailableJob(null));
    clearInvocations(worker);
    return item.getId();
  }

  private SaveInboxItemCommand saveCommand() {
    return SaveInboxItemCommand.builder()
        .direction(TransactionDirection.EXPENSE)
        .amount(new BigDecimal("91.25"))
        .description("Fixture")
        .date(LocalDate.of(2026, 8, 24))
        .activityId(activityId())
        .neutralCategoryId(neutralCategoryId("OTHER_EXPENSE"))
        .build();
  }

  private LedgerTransactionInput receiptInput(String origin, String driveItemId, int year) {
    return LedgerTransactionInput.builder()
        .amount(new BigDecimal("18.40"))
        .direction(TransactionDirection.EXPENSE)
        .date(LocalDate.of(year, 3, 9))
        .description("Hardware")
        .activityId(activityId())
        .neutralCategoryId(neutralCategoryId("OTHER_EXPENSE"))
        .origin(origin)
        .externalId(driveItemId)
        .attachmentExternalId(driveItemId)
        .attachmentFileName("receipt.pdf")
        .build();
  }

  private List<Map<String, Object>> moveJobs(String driveItemId) {
    return jdbcTemplate.queryForList(
        """
        SELECT type, state, target_year, inbox_item_id FROM background_jobs
        WHERE source_external_id = ?
        """,
        driveItemId);
  }

  private Long activityId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM financial_activities WHERE system_key = 'NEEDS_CLASSIFICATION'",
        Long.class);
  }

  private Long neutralCategoryId(String key) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM neutral_categories WHERE category_key = ?", Long.class, key);
  }

  private long transactionsFor(String externalId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM transaction_import_references WHERE external_id = ?",
        Long.class,
        externalId);
  }
}
