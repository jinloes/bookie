package com.bookie.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.model.ExpenseSource;
import com.bookie.model.OutlookEmail;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.ReceiptDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AutoImportPollingServiceTest {

  @Mock private OutlookService outlookService;
  @Mock private ReceiptService receiptService;
  @Mock private PendingExpenseService pendingExpenseService;
  @Mock private EmailParseQueueService emailParseQueueService;
  @Mock private ReceiptParseQueueService receiptParseQueueService;
  @Mock private MsalTokenService msalTokenService;

  @InjectMocks private AutoImportPollingService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "maxItemsPerPoll", 20);
  }

  private static PendingExpense pending(Long id) {
    return PendingExpense.builder().id(id).status(PendingExpenseStatus.PROCESSING).build();
  }

  @Nested
  class PollForNewItems {

    @Test
    void skipsEntirelyWhenDisabled() {
      ReflectionTestUtils.setField(service, "enabled", false);

      service.pollForNewItems();

      verifyNoInteractions(outlookService, receiptService, pendingExpenseService);
    }

    @Test
    void skipsWhenNotConnectedToOutlook() {
      when(msalTokenService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verifyNoInteractions(outlookService, receiptService, pendingExpenseService);
    }

    @Test
    void queuesNewEmailsWithNoExistingPendingRecord() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail newEmail =
          OutlookEmail.builder().id("msg-1").subject("Rent for June").pendingId(null).build();
      when(outlookService.getRentalEmails(0, java.time.Year.now().getValue()))
          .thenReturn(new OutlookEmailsPage(List.of(newEmail), 0, false));
      when(pendingExpenseService.findOrCreate(
              "msg-1", ExpenseSource.OUTLOOK_EMAIL, "Rent for June"))
          .thenReturn(new PendingExpenseService.FindOrCreateResult(pending(5L), false));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(emailParseQueueService).processEmail(5L, "msg-1");
    }

    @Test
    void skipsEmailsThatAlreadyHaveAPendingRecord() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail alreadyTracked =
          OutlookEmail.builder().id("msg-2").subject("Rent").pendingId(9L).build();
      when(outlookService.getRentalEmails(0, java.time.Year.now().getValue()))
          .thenReturn(new OutlookEmailsPage(List.of(alreadyTracked), 0, false));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(pendingExpenseService, never()).findOrCreate(anyString(), any(), anyString());
      verify(emailParseQueueService, never()).processEmail(any(), anyString());
    }

    @Test
    void doesNotQueueEmailAgainWhenAlreadyProcessing() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail newEmail =
          OutlookEmail.builder().id("msg-3").subject("Rent").pendingId(null).build();
      when(outlookService.getRentalEmails(0, java.time.Year.now().getValue()))
          .thenReturn(new OutlookEmailsPage(List.of(newEmail), 0, false));
      when(pendingExpenseService.findOrCreate("msg-3", ExpenseSource.OUTLOOK_EMAIL, "Rent"))
          .thenReturn(new PendingExpenseService.FindOrCreateResult(pending(6L), true));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(emailParseQueueService, never()).processEmail(any(), anyString());
    }

    @Test
    void doesNotQueueEmailIfQueueServiceFails() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail newEmail =
          OutlookEmail.builder().id("msg-4").subject("Rent").pendingId(null).build();
      when(outlookService.getRentalEmails(0, java.time.Year.now().getValue()))
          .thenReturn(new OutlookEmailsPage(List.of(newEmail), 0, false));
      when(pendingExpenseService.findOrCreate("msg-4", ExpenseSource.OUTLOOK_EMAIL, "Rent"))
          .thenReturn(new PendingExpenseService.FindOrCreateResult(pending(7L), false));
      doThrow(new RuntimeException("queue service down"))
          .when(emailParseQueueService)
          .processEmail(7L, "msg-4");
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      // Email was created but not successfully queued, returns false
      verify(emailParseQueueService).processEmail(7L, "msg-4");
    }

    @Test
    void paginatesThroughAllEmailPagesUntilNoMore() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail first = OutlookEmail.builder().id("msg-a").subject("A").pendingId(null).build();
      OutlookEmail second = OutlookEmail.builder().id("msg-b").subject("B").pendingId(null).build();
      int year = java.time.Year.now().getValue();
      when(outlookService.getRentalEmails(0, year))
          .thenReturn(new OutlookEmailsPage(List.of(first), 0, true));
      when(outlookService.getRentalEmails(1, year))
          .thenReturn(new OutlookEmailsPage(List.of(second), 1, false));
      when(pendingExpenseService.findOrCreate(anyString(), any(), anyString()))
          .thenReturn(new PendingExpenseService.FindOrCreateResult(pending(1L), false));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(outlookService).getRentalEmails(0, year);
      verify(outlookService).getRentalEmails(1, year);
      verify(emailParseQueueService, times(2)).processEmail(any(), anyString());
    }

    @Test
    void stopsQueueingEmailsOncePollBudgetIsReached() {
      ReflectionTestUtils.setField(service, "maxItemsPerPoll", 1);
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail first = OutlookEmail.builder().id("msg-a").subject("A").pendingId(null).build();
      OutlookEmail second = OutlookEmail.builder().id("msg-b").subject("B").pendingId(null).build();
      int year = java.time.Year.now().getValue();
      when(outlookService.getRentalEmails(0, year))
          .thenReturn(new OutlookEmailsPage(List.of(first, second), 0, true));
      when(pendingExpenseService.findOrCreate(anyString(), any(), anyString()))
          .thenReturn(new PendingExpenseService.FindOrCreateResult(pending(1L), false));
      when(receiptService.isConnected()).thenReturn(false);

      service.pollForNewItems();

      verify(outlookService, never()).getRentalEmails(1, year);
      verify(emailParseQueueService, times(1)).processEmail(any(), anyString());
    }

    @Test
    void continuesPollingReceiptsWhenEmailQueueFails() {
      when(msalTokenService.isConnected()).thenReturn(true);
      OutlookEmail failing =
          OutlookEmail.builder().id("msg-x").subject("X").pendingId(null).build();
      when(outlookService.getRentalEmails(0, java.time.Year.now().getValue()))
          .thenReturn(new OutlookEmailsPage(List.of(failing), 0, false));
      when(pendingExpenseService.findOrCreate("msg-x", ExpenseSource.OUTLOOK_EMAIL, "X"))
          .thenThrow(new RuntimeException("boom"));
      when(receiptService.isConnected()).thenReturn(true);
      when(receiptService.listReceipts()).thenReturn(List.of());

      service.pollForNewItems();

      verify(emailParseQueueService, never()).processEmail(any(), anyString());
      verify(receiptService).listReceipts();
    }

    @Test
    void queuesNewPendingReceiptsWithNoExistingPendingRecord() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, java.time.Year.now().getValue()))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto receipt =
          new ReceiptDto("item-1", "receipt.pdf", 0, "http://x", "2026-01-01", null, null, true);
      when(receiptService.listReceipts()).thenReturn(List.of(receipt));
      when(pendingExpenseService.findBySourceId("item-1")).thenReturn(Optional.empty());
      when(pendingExpenseService.findOrCreate("item-1", ExpenseSource.RECEIPT, "receipt.pdf"))
          .thenReturn(new PendingExpenseService.FindOrCreateResult(pending(7L), false));

      service.pollForNewItems();

      verify(receiptParseQueueService).processReceipt(7L, "item-1");
    }

    @Test
    void skipsReceiptsThatAlreadyHaveAPendingRecord() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, java.time.Year.now().getValue()))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto receipt =
          new ReceiptDto("item-2", "receipt.pdf", 0, "http://x", "2026-01-01", null, null, true);
      when(receiptService.listReceipts()).thenReturn(List.of(receipt));
      when(pendingExpenseService.findBySourceId("item-2")).thenReturn(Optional.of(pending(1L)));

      service.pollForNewItems();

      verify(receiptParseQueueService, never()).processReceipt(any(), anyString());
    }

    @Test
    void skipsReceiptsThatAreNotPendingOrAlreadySaved() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, java.time.Year.now().getValue()))
          .thenReturn(new OutlookEmailsPage(List.of(), 0, false));
      when(receiptService.isConnected()).thenReturn(true);
      ReceiptDto organized =
          new ReceiptDto(
              "item-3", "receipt.pdf", 2025, "http://x", "2025-01-01", null, null, false);
      ReceiptDto alreadySaved =
          new ReceiptDto("item-4", "receipt.pdf", 0, "http://x", "2026-01-01", 11L, null, true);
      when(receiptService.listReceipts()).thenReturn(List.of(organized, alreadySaved));

      service.pollForNewItems();

      verify(pendingExpenseService, never()).findBySourceId(anyString());
      verify(receiptParseQueueService, never()).processReceipt(any(), anyString());
    }

    @Test
    void skipsReceiptPollingWhenNotConnectedToOneDrive() {
      when(msalTokenService.isConnected()).thenReturn(true);
      when(outlookService.getRentalEmails(0, java.time.Year.now().getValue()))
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
  }
}
