package com.bookie.intake.sources;

import com.bookie.datalifecycle.backup.BackupService;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.sources.InboxItemCreationService.FindOrCreateResult;
import com.bookie.intake.sources.InboxItemCreationService.IntakeSource;
import com.bookie.integrations.outlook.OutlookAuthorization;
import com.bookie.ledger.domain.ExpenseSource;
import java.time.Clock;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically checks watched Outlook folders and configured OneDrive receipt folders for items
 * that haven't been queued for parsing yet, and queues them automatically.
 *
 * <p>Without this, "automatic bookkeeping" required the user to remember to open the Emails or
 * Receipts page and manually trigger a parse for every new item — this closes that gap by reusing
 * the same {@link InboxItemCreationService#findOrCreate} path the manual "Parse" buttons already
 * use, so behavior (dedup, conflict handling, parse kickoff, SSE notification) is identical either
 * way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoImportPollingService {

  // Venmo and bank statements archived to pending/ are not receipts and have no extractable text.
  private static final String[] STATEMENT_EXTENSIONS = {".csv", ".ofx", ".qfx"};

  private final Clock clock;

  private final OutlookService outlookService;
  private final OutlookEmailIntakeService outlookEmailIntakeService;
  private final ReceiptService receiptService;
  private final InboxItemCreationService inboxItemCreationService;
  private final InboxItemStore inboxItemStore;
  private final OutlookAuthorization outlookAuthorization;

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
    if (!enabled || !outlookAuthorization.isConnected()) {
      return;
    }
    try {
      int queued = pollEmails();
      queued += pollReceipts(queued);
      if (queued > 0) {
        log.info("Auto-import poll queued {} new item(s) for parsing", queued);
      }
    } catch (Exception e) {
      log.error("Auto-import poll failed unexpectedly", e);
    }
  }

  /** Queues any new rental emails (no active inbox item) up to {@link #maxItemsPerPoll}. */
  private int pollEmails() {
    int queued = 0;
    int page = 0;
    boolean hasMore = true;
    while (hasMore && queued < maxItemsPerPoll) {
      OutlookEmailsPage result = outlookService.getRentalEmails(page, Year.now(clock).getValue());
      for (OutlookEmail email : result.emails()) {
        if (queued >= maxItemsPerPoll) {
          break;
        }
        if (email.inboxItemId() != null) {
          continue;
        }
        queued += queueEmail(email);
      }
      hasMore = result.hasMore();
      page++;
    }
    return queued;
  }

  private int queueEmail(OutlookEmail email) {
    try {
      return outlookEmailIntakeService.queue(email.id(), email.activityId()).queuedCount();
    } catch (Exception e) {
      log.warn("Failed to auto-queue email {} for parsing", email.id(), e);
      return 0;
    }
  }

  /**
   * Queues any pending receipts (loose files, unorganized subfolders, or configured import folders)
   * that don't already have an active inbox item, up to the remaining {@link #maxItemsPerPoll}
   * budget for this cycle.
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
      if (StringUtils.endsWithAny(StringUtils.lowerCase(receipt.name()), STATEMENT_EXTENSIONS)) {
        continue;
      }
      if (hasActiveInboxItem(receipt.id())) {
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
          inboxItemCreationService.findOrCreate(
              IntakeSource.of(receipt.id(), ExpenseSource.RECEIPT, receipt.name()));
      // Counts only items this poll queued; the parse kick follows their commit (InboxItemQueued).
      // An item already processing means a concurrent request won the race.
      return !result.alreadyProcessing();
    } catch (Exception e) {
      log.warn("Failed to auto-queue receipt {} for parsing", receipt.id(), e);
      return false;
    }
  }

  private boolean hasActiveInboxItem(String receiptId) {
    return inboxItemStore
        .findBySourceIdentity(ExpenseSource.RECEIPT, receiptId)
        .filter(
            item -> item.getState() != InboxState.SAVED && item.getState() != InboxState.DISMISSED)
        .isPresent();
  }
}
