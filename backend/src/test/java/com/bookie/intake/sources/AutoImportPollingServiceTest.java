package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.integrations.outlook.OutlookAuthorization;
import com.bookie.ledger.domain.ExpenseSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AutoImportPollingServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC);

  @Mock private OutlookService outlookService;
  @Mock private OutlookEmailIntakeService outlookEmailIntakeService;
  @Mock private ReceiptService receiptService;
  @Mock private InboxItemCreationService inboxItemCreationService;
  @Mock private InboxItemStore inboxItemStore;
  @Mock private OutlookAuthorization msalTokenService;

  private AutoImportPollingService service;

  @BeforeEach
  void setUp() {
    service =
        new AutoImportPollingService(
            CLOCK,
            outlookService,
            outlookEmailIntakeService,
            receiptService,
            inboxItemCreationService,
            inboxItemStore,
            msalTokenService,
            () -> false);
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "maxItemsPerPoll", 20);
  }

  private static InboxItem item(Long id, InboxState state) {
    return InboxItem.builder().id(id).origin(ExpenseSource.RECEIPT).state(state).build();
  }

  private static ReceiptDto pendingReceipt(String id) {
    return new ReceiptDto(id, "receipt.pdf", 0, "http://x", "2026-01-01", null, null, true);
  }

  private static InboxItemCreationService.IntakeSource receiptSource(String id) {
    return InboxItemCreationService.IntakeSource.of(id, ExpenseSource.RECEIPT, "receipt.pdf");
  }

  @Nested
  class PollForNewItems {

    @Test
    void skipsEntirelyWhenDisabled() {
      ReflectionTestUtils.setField(service, "enabled", false);

      service.pollForNewItems();

      verifyNoInteractions(outlookService, receiptService, inboxItemCreationService);
    }

    @Test
    void skipsWhenNotConnectedToOutlook() {
      when(msalTokenService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verifyNoInteractions(outlookService, receiptService, inboxItemCreationService);
    }

    @Test
    void queuesNewEmailsWithNoExistingPendingRecord() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail newEmail =
          OutlookEmail.builder()
              .id("msg-1")
              .subject("Rent for June")
              .inboxItemId(null)
              .activityId(42L)
              .build();
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(newEmail), 0, false));
      when(outlookEmailIntakeService.queue("msg-1", 42L))
          .thenReturn(
              new OutlookEmailIntakeService.QueueResult(
                  List.of(
                      InboxItem.builder()
                          .id(5L)
                          .configuredActivityId(42L)
                          .state(InboxState.QUEUED)
                          .build()),
                  1));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(outlookEmailIntakeService).queue("msg-1", 42L);
    }

    @Test
    void skipsEmailsThatAlreadyHaveAPendingRecord() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail alreadyTracked =
          OutlookEmail.builder().id("msg-2").subject("Rent").inboxItemId(9L).build();
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(alreadyTracked), 0, false));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(outlookEmailIntakeService, never()).queue(anyString(), any());
    }

    @Test
    void doesNotQueueEmailAgainWhenAlreadyProcessing() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail newEmail =
          OutlookEmail.builder().id("msg-3").subject("Rent").inboxItemId(null).build();
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(newEmail), 0, false));
      when(outlookEmailIntakeService.queue("msg-3", null))
          .thenReturn(
              new OutlookEmailIntakeService.QueueResult(List.of(item(6L, InboxState.QUEUED)), 0));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(outlookEmailIntakeService).queue("msg-3", null);
    }

    @Test
    void doesNotQueueEmailIfQueueServiceFails() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail newEmail =
          OutlookEmail.builder().id("msg-4").subject("Rent").inboxItemId(null).build();
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(newEmail), 0, false));
      doThrow(new RuntimeException("queue service down"))
          .when(outlookEmailIntakeService)
          .queue("msg-4", null);
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(outlookEmailIntakeService).queue("msg-4", null);
    }

    @Test
    void paginatesThroughAllEmailPagesUntilNoMore() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail first =
          OutlookEmail.builder().id("msg-a").subject("A").inboxItemId(null).build();
      OutlookEmail second =
          OutlookEmail.builder().id("msg-b").subject("B").inboxItemId(null).build();
      int year = 2026;
      when(outlookService.getRentalEmails(0, year))
          .thenReturn(new OutlookEmailsPage(List.of(first), 0, true));
      when(outlookService.getRentalEmails(1, year))
          .thenReturn(new OutlookEmailsPage(List.of(second), 1, false));
      when(outlookEmailIntakeService.queue(anyString(), any()))
          .thenReturn(
              new OutlookEmailIntakeService.QueueResult(List.of(item(1L, InboxState.QUEUED)), 1));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(outlookService).getRentalEmails(0, year);
      verify(outlookService).getRentalEmails(1, year);
      verify(outlookEmailIntakeService, times(2)).queue(anyString(), any());
    }

    @Test
    void stopsQueueingEmailsOncePollBudgetIsReached() {
      ReflectionTestUtils.setField(service, "maxItemsPerPoll", 1);
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail first =
          OutlookEmail.builder().id("msg-a").subject("A").inboxItemId(null).build();
      OutlookEmail second =
          OutlookEmail.builder().id("msg-b").subject("B").inboxItemId(null).build();
      int year = 2026;
      when(outlookService.getRentalEmails(0, year))
          .thenReturn(new OutlookEmailsPage(List.of(first, second), 0, true));
      when(outlookEmailIntakeService.queue(anyString(), any()))
          .thenReturn(
              new OutlookEmailIntakeService.QueueResult(
                  List.of(
                      item(1L, InboxState.QUEUED),
                      item(2L, InboxState.QUEUED),
                      item(3L, InboxState.QUEUED)),
                  3));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(outlookService, never()).getRentalEmails(1, year);
      verify(outlookEmailIntakeService, times(1)).queue(anyString(), any());
    }

    @Test
    void continuesPollingReceiptsWhenEmailQueueFails() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail failing =
          OutlookEmail.builder().id("msg-x").subject("X").inboxItemId(null).build();
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(failing), 0, false));
      when(outlookEmailIntakeService.queue("msg-x", null)).thenThrow(new RuntimeException("boom"));
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.listReceipts()).thenReturn(List.of());

      service.pollForNewItems();

      verify(receiptService).listReceipts();
    }

    @Test
    void queuesNewReceiptsWithNoActiveInboxItem() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto receipt =
          new ReceiptDto("item-1", "receipt.pdf", 0, "http://x", "2026-01-01", null, null, true);
      when(receiptService.listReceipts()).thenReturn(List.of(receipt));
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "item-1"))
          .thenReturn(Optional.empty());
      when(inboxItemCreationService.findOrCreate(
              InboxItemCreationService.IntakeSource.of(
                  "item-1", ExpenseSource.RECEIPT, "receipt.pdf")))
          .thenReturn(
              new InboxItemCreationService.FindOrCreateResult(item(7L, InboxState.QUEUED), false));

      service.pollForNewItems();

      // findOrCreate publishes the parse kick (InboxItemQueued) with the item's commit.
      verify(inboxItemCreationService)
          .findOrCreate(
              InboxItemCreationService.IntakeSource.of(
                  "item-1", ExpenseSource.RECEIPT, "receipt.pdf"));
    }

    @Test
    void aReceiptAlreadyProcessingOrFailingToQueueDoesNotConsumeThePollBudget() {
      ReflectionTestUtils.setField(service, "maxItemsPerPoll", 1);
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto concurrent = pendingReceipt("item-6");
      ReceiptDto failing = pendingReceipt("item-7");
      ReceiptDto queued = pendingReceipt("item-8");
      ReceiptDto overBudget = pendingReceipt("item-9");
      when(receiptService.listReceipts())
          .thenReturn(List.of(concurrent, failing, queued, overBudget));
      when(inboxItemStore.findBySourceIdentity(any(), anyString())).thenReturn(Optional.empty());
      when(inboxItemCreationService.findOrCreate(receiptSource("item-6")))
          .thenReturn(
              new InboxItemCreationService.FindOrCreateResult(item(6L, InboxState.QUEUED), true));
      when(inboxItemCreationService.findOrCreate(receiptSource("item-7")))
          .thenThrow(new RuntimeException("boom"));
      when(inboxItemCreationService.findOrCreate(receiptSource("item-8")))
          .thenReturn(
              new InboxItemCreationService.FindOrCreateResult(item(8L, InboxState.QUEUED), false));

      service.pollForNewItems();

      verify(inboxItemCreationService, times(3)).findOrCreate(any());
      verify(inboxItemCreationService, never()).findOrCreate(receiptSource("item-9"));
    }

    @Test
    void skipsReceiptsThatAlreadyHaveAnActiveInboxItem() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto receipt =
          new ReceiptDto("item-2", "receipt.pdf", 0, "http://x", "2026-01-01", null, null, true);
      when(receiptService.listReceipts()).thenReturn(List.of(receipt));
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "item-2"))
          .thenReturn(Optional.of(item(1L, InboxState.READY)));

      service.pollForNewItems();

      verifyNoInteractions(inboxItemCreationService);
    }

    @Test
    void requeuesAReceiptWhoseInboxItemWasDismissed() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto receipt =
          new ReceiptDto("item-5", "receipt.pdf", 0, "http://x", "2026-01-01", null, null, true);
      when(receiptService.listReceipts()).thenReturn(List.of(receipt));
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "item-5"))
          .thenReturn(Optional.of(item(9L, InboxState.DISMISSED)));
      when(inboxItemCreationService.findOrCreate(
              InboxItemCreationService.IntakeSource.of(
                  "item-5", ExpenseSource.RECEIPT, "receipt.pdf")))
          .thenReturn(
              new InboxItemCreationService.FindOrCreateResult(item(9L, InboxState.QUEUED), false));

      service.pollForNewItems();

      verify(inboxItemCreationService).findOrCreate(receiptSource("item-5"));
    }

    @Test
    void skipsReceiptsThatAreNotPendingOrAlreadySaved() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto organized =
          new ReceiptDto(
              "item-3", "receipt.pdf", 2025, "http://x", "2025-01-01", null, null, false);
      ReceiptDto alreadySaved =
          new ReceiptDto("item-4", "receipt.pdf", 0, "http://x", "2026-01-01", 11L, null, true);
      when(receiptService.listReceipts()).thenReturn(List.of(organized, alreadySaved));

      service.pollForNewItems();

      verifyNoInteractions(inboxItemStore, inboxItemCreationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"statement.csv", "STATEMENT.CSV", "bank.ofx", "bank.qfx"})
    void skipsPendingStatementFiles(String name) {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto statement =
          new ReceiptDto("item-10", name, 0, "http://x", "2026-01-01", null, null, true);
      when(receiptService.listReceipts()).thenReturn(List.of(statement));

      service.pollForNewItems();

      verifyNoInteractions(inboxItemStore, inboxItemCreationService);
    }

    @Test
    void skipsReceiptPollingWhenNotConnectedToOneDrive() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(receiptService, never()).listReceipts();
    }

    @Test
    void logsAndSwallowsUnexpectedExceptionsFromPolling() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(anyInt(), anyInt()))
          .thenThrow(new RuntimeException("kaboom"));

      service.pollForNewItems();

      // No exception propagates — polling failures must never crash the scheduler thread.
    }

    @Test
    void startupOnlyImportsOnTheFirstRunAndSkipsLaterRuns() {
      ReflectionTestUtils.setField(service, "startupOnly", true);
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();
      service.pollForNewItems();

      verify(msalTokenService, times(1)).isConnected();
      verify(outlookService, times(1)).getRentalEmails(0, 2026);
    }
  }

  @Nested
  class RunNow {

    @Test
    void skipsWhileWritesAreFrozen() {
      AutoImportPollingService frozen =
          new AutoImportPollingService(
              CLOCK,
              outlookService,
              outlookEmailIntakeService,
              receiptService,
              inboxItemCreationService,
              inboxItemStore,
              msalTokenService,
              () -> true);
      ReflectionTestUtils.setField(frozen, "enabled", true);

      assertThat(frozen.runNow()).isZero();

      verifyNoInteractions(msalTokenService, outlookService, receiptService);
    }

    @Test
    void runsEveryTimeEvenInStartupOnlyMode() {
      ReflectionTestUtils.setField(service, "startupOnly", true);
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();
      service.runNow();

      verify(outlookService, times(2)).getRentalEmails(0, 2026);
    }

    @Test
    void returnsTheNumberOfQueuedItems() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail newEmail = OutlookEmail.builder().id("m-1").activityId(42L).build();
      when(outlookService.getRentalEmails(0, 2026))
          .thenReturn(new OutlookEmailsPage(List.of(newEmail), 0, false));
      when(outlookEmailIntakeService.queue("m-1", 42L))
          .thenReturn(
              new OutlookEmailIntakeService.QueueResult(
                  List.of(InboxItem.builder().id(5L).state(InboxState.QUEUED).build()), 1));
      when(receiptService.isConnected()).thenReturn(false);

      assertThat(service.runNow()).isEqualTo(1);
    }

    @Test
    void returnsZeroWhenPollingFails() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(anyInt(), anyInt()))
          .thenThrow(new RuntimeException("kaboom"));

      assertThat(service.runNow()).isZero();
    }
  }
}
