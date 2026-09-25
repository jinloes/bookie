package com.bookie.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.application.InboxCommands;
import com.bookie.intake.application.SaveInboxItemCommand;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.intake.sources.EventPublicationResubmitter;
import com.bookie.intake.sources.InboxItemCreationService;
import com.bookie.integrations.onedrive.OneDrivePort;
import com.bookie.ledger.application.LedgerTransactionInput;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.domain.ExpenseSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pins the event publication registry: each event is registered in the transaction that publishes
 * it, its listener runs once after commit, and the completed publication is deleted.
 */
// One context for the class, closed afterwards together with its in-memory database (no
// DB_CLOSE_DELAY), so it adds nothing to the suite's cached contexts; each test isolates itself by
// source identity instead of by a fresh database.
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:intake_event_publication",
      "spring.jpa.hibernate.ddl-auto=validate",
      "bookie.auto-import.enabled=false",
      "bookie.intake.worker.enabled=false"
    })
@DirtiesContext
class IntakeEventPublicationIntegrationTest {

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
  @Autowired private IncompleteEventPublications incompletePublications;
  @Autowired private Clock clock;
  @MockitoBean private OneDrivePort oneDrive;

  @BeforeEach
  void awaitPreviousPublications() {
    await().atMost(WAIT).untilAsserted(() -> assertThat(publications()).isEmpty());
  }

  @Nested
  class InboxKickoffs {

    @Test
    void aSaveRegistersItsKickInTheSaveTransactionKicksOnceAfterCommitAndDeletesIt()
        throws Exception {
      Long itemId = readyReceipt();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicBoolean transactionActive = new AtomicBoolean(true);
      doAnswer(
              invocation -> {
                transactionActive.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
                entered.countDown();
                release.await(10, TimeUnit.SECONDS);
                return null;
              })
          .when(worker)
          .runAvailableJob(any());

      transactionally(
          () -> {
            inboxCommands.save(itemId, saveCommand());
            assertThat(publications())
                .singleElement()
                .satisfies(
                    row -> {
                      assertThat(row.get("EVENT_TYPE"))
                          .isEqualTo("com.bookie.intake.application.InboxItemSaved");
                      assertThat((String) row.get("LISTENER_ID"))
                          .contains("IntakeJobKickoffListener");
                    });
            assertThat(entered.getCount()).isOne();
          });

      // The worker's methods are synchronized, so wait on the latch rather than verify() while the
      // kick is held.
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(transactionActive).isFalse();
      assertThat(publications())
          .singleElement()
          .satisfies(row -> assertThat(row.get("COMPLETION_DATE")).isNull());
      release.countDown();
      await().atMost(WAIT).untilAsserted(() -> assertThat(publications()).isEmpty());
      verify(worker, times(1)).runAvailableJob(null);
    }

    @Test
    void aRolledBackSaveLeavesNoPublicationAndNoKick() {
      Long itemId = readyReceipt();

      assertThatThrownBy(
              () ->
                  transactionally(
                      () -> {
                        inboxCommands.save(itemId, saveCommand());
                        assertThat(publications()).hasSize(1);
                        throw new IllegalStateException("force rollback");
                      }))
          .hasMessage("force rollback");

      assertThat(publications()).isEmpty();
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(worker, never()).runAvailableJob(any()));
    }

    @Test
    void aQueuedItemRegistersItsParseKickAndCompletesItAfterCommit() {
      creation.findOrCreate(
          InboxItemCreationService.IntakeSource.of(
              "receipt-" + UUID.randomUUID(), ExpenseSource.RECEIPT, "queued.pdf"));

      await().atMost(WAIT).untilAsserted(() -> verify(worker).runAvailableJob(null));
      await().atMost(WAIT).untilAsserted(() -> assertThat(publications()).isEmpty());
      verify(worker, times(1)).runAvailableJob(null);
    }
  }

  @Nested
  class ReceiptFiling {

    @BeforeEach
    void stubOneDrive() {
      when(oneDrive.ensureFolder(any(), any())).thenReturn("folder-2026");
    }

    @Test
    void aReceiptCreateRegistersItsFilingAndFilesTheReceiptOnceOnlyAfterCommit() {
      transactionally(
          () -> {
            ledgerTransactions.create(receiptInput("RECEIPT", "drive-7"));
            assertThat(publications())
                .singleElement()
                .satisfies(
                    row -> {
                      assertThat(row.get("EVENT_TYPE"))
                          .isEqualTo("com.bookie.ledger.application.TransactionRecorded");
                      assertThat((String) row.get("LISTENER_ID")).contains("ReceiptFilingListener");
                    });
            verify(oneDrive, never()).move(any(), any());
          });

      await().atMost(WAIT).untilAsserted(() -> verify(oneDrive).move("drive-7", "folder-2026"));
      await().atMost(WAIT).untilAsserted(() -> assertThat(publications()).isEmpty());
      verify(oneDrive, times(1)).move(any(), any());
    }

    @Test
    void aNonReceiptCreatePublishesNothingToFile() {
      transactionally(
          () -> {
            ledgerTransactions.create(receiptInput("MANUAL", "drive-9"));
            assertThat(publications()).isEmpty();
          });

      assertThat(transactionsFor("drive-9")).isOne();
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(oneDrive, never()).move(any(), any()));
    }

    @Test
    void aRolledBackReceiptCreateLeavesNoPublicationAndFilesNothing() {
      assertThatThrownBy(
              () ->
                  transactionally(
                      () -> {
                        ledgerTransactions.create(receiptInput("RECEIPT", "drive-8"));
                        assertThat(publications()).hasSize(1);
                        throw new IllegalStateException("force rollback");
                      }))
          .hasMessage("force rollback");

      assertThat(transactionsFor("drive-8")).isZero();
      assertThat(publications()).isEmpty();
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(oneDrive, never()).move(any(), any()));
    }

    @Test
    void aFailedFilingStaysIncompleteUntilAResubmissionCompletesIt() {
      doThrow(new IllegalStateException("OneDrive offline"))
          .doNothing()
          .when(oneDrive)
          .move("drive-6", "folder-2026");

      ledgerTransactions.create(receiptInput("RECEIPT", "drive-6"));

      await().atMost(WAIT).untilAsserted(() -> verify(oneDrive).move("drive-6", "folder-2026"));
      await()
          .atMost(WAIT)
          .untilAsserted(
              () ->
                  assertThat(publications())
                      .singleElement()
                      .satisfies(row -> assertThat(row.get("COMPLETION_DATE")).isNull()));
      // A clock past the resubmitter's five-minute minimum age makes the failed publication due.
      EventPublicationResubmitter resubmitter =
          new EventPublicationResubmitter(
              incompletePublications, Clock.offset(clock, Duration.ofMinutes(6)));
      resubmitter.ready();

      resubmitter.resubmit();

      await()
          .atMost(WAIT)
          .untilAsserted(() -> verify(oneDrive, times(2)).move("drive-6", "folder-2026"));
      await().atMost(WAIT).untilAsserted(() -> assertThat(publications()).isEmpty());
      assertThat(transactionsFor("drive-6")).isOne();
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
    // Let the item's parse kick finish so each test observes only its own publications.
    await().atMost(WAIT).untilAsserted(() -> verify(worker).runAvailableJob(null));
    await().atMost(WAIT).untilAsserted(() -> assertThat(publications()).isEmpty());
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

  private LedgerTransactionInput receiptInput(String origin, String driveItemId) {
    return LedgerTransactionInput.builder()
        .amount(new BigDecimal("18.40"))
        .direction(TransactionDirection.EXPENSE)
        .date(LocalDate.of(2026, 3, 9))
        .description("Hardware")
        .activityId(activityId())
        .neutralCategoryId(neutralCategoryId("OTHER_EXPENSE"))
        .origin(origin)
        .externalId(driveItemId)
        .attachmentExternalId(driveItemId)
        .attachmentFileName("receipt.pdf")
        .build();
  }

  private List<Map<String, Object>> publications() {
    return jdbcTemplate.queryForList(
        "SELECT EVENT_TYPE, LISTENER_ID, COMPLETION_DATE FROM EVENT_PUBLICATION");
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
