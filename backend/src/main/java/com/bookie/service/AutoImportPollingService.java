package com.bookie.service;

import com.bookie.model.ExpenseSource;
import com.bookie.model.OutlookEmail;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.model.ReceiptDto;
import com.bookie.service.PendingExpenseService.FindOrCreateResult;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically checks watched Outlook folders and configured OneDrive receipt folders for items
 * that haven't been queued for parsing yet, and queues them automatically.
 *
 * <p>Without this, "automatic bookkeeping" required the user to remember to open the Emails or
 * Receipts page and manually trigger a parse for every new item — this closes that gap by reusing
 * the same {@link PendingExpenseService#findOrCreate} + queue-service path the manual "Parse"
 * buttons already use, so behavior (dedup, conflict handling, SSE notification) is identical either
 * way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoImportPollingService {

  private final OutlookService outlookService;
  private final ReceiptService receiptService;
  private final PendingExpenseService pendingExpenseService;
  private final EmailParseQueueService emailParseQueueService;
  private final ReceiptParseQueueService receiptParseQueueService;
  private final MsalTokenService msalTokenService;

  @Value("${bookie.auto-import.enabled:true}")
  private boolean enabled;

  @Value("${bookie.auto-import.max-items-per-poll:20}")
  private int maxItemsPerPoll;

  /**
   * Runs on a fixed delay (default 30 minutes, configurable via {@code
   * bookie.auto-import.poll-interval-ms}). Skips silently if Outlook/OneDrive isn't connected —
   * mirrors {@link BackupService#scheduledBackup()}'s connection guard.
   */
  @Scheduled(
      initialDelayString = "${bookie.auto-import.initial-delay-ms:60000}",
      fixedDelayString = "${bookie.auto-import.poll-interval-ms:1800000}")
  public void pollForNewItems() {
    if (!enabled || !msalTokenService.isConnected()) {
      return;
    }
    try {
      int queued = pollEmails();
      queued += pollReceipts(queued);
      if (queued > 0) {
        log.info("Auto-import poll queued {} new item(s) for parsing", queued);
      }
    } catch (Exception e) {
      log.error("Auto-import poll failed", e);
    }
  }

  /** Queues any new rental emails (no existing pending record) up to {@link #maxItemsPerPoll}. */
  private int pollEmails() {
    int queued = 0;
    int page = 0;
    boolean hasMore = true;
    while (hasMore && queued < maxItemsPerPoll) {
      OutlookEmailsPage result = outlookService.getRentalEmails(page, Year.now().getValue());
      for (OutlookEmail email : result.emails()) {
        if (queued >= maxItemsPerPoll) {
          break;
        }
        if (email.pendingId() != null) {
          continue;
        }
        if (queueEmail(email)) {
          queued++;
        }
      }
      hasMore = result.hasMore();
      page++;
    }
    return queued;
  }

  private boolean queueEmail(OutlookEmail email) {
    try {
      FindOrCreateResult result =
          pendingExpenseService.findOrCreate(
              email.id(), ExpenseSource.OUTLOOK_EMAIL, email.subject());
      if (!result.alreadyProcessing()) {
        emailParseQueueService.processEmail(result.pending().getId(), email.id());
        return true;
      }
    } catch (Exception e) {
      log.warn("Failed to auto-queue email {} for parsing", email.id(), e);
    }
    return false;
  }

  /**
   * Queues any pending receipts (loose files, unorganized subfolders, or configured import folders)
   * that don't already have a pending record, up to the remaining {@link #maxItemsPerPoll} budget
   * for this cycle.
   */
  private int pollReceipts(int alreadyQueued) {
    if (!receiptService.isConnected()) {
      return 0;
    }
    int queued = 0;
    int budget = maxItemsPerPoll - alreadyQueued;
    for (ReceiptDto receipt : receiptService.listReceipts()) {
      if (queued >= budget) {
        break;
      }
      if (!receipt.pending() || receipt.expenseId() != null || receipt.incomeId() != null) {
        continue;
      }
      if (pendingExpenseService.findBySourceId(receipt.id()).isPresent()) {
        continue;
      }
      if (queueReceipt(receipt)) {
        queued++;
      }
    }
    return queued;
  }

  private boolean queueReceipt(ReceiptDto receipt) {
    try {
      FindOrCreateResult result =
          pendingExpenseService.findOrCreate(receipt.id(), ExpenseSource.RECEIPT, receipt.name());
      if (!result.alreadyProcessing()) {
        receiptParseQueueService.processReceipt(result.pending().getId(), receipt.id());
        return true;
      }
    } catch (Exception e) {
      log.warn("Failed to auto-queue receipt {} for parsing", receipt.id(), e);
    }
    return false;
  }
}
