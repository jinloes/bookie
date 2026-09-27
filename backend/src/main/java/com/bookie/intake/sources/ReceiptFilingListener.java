package com.bookie.intake.sources;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.ledger.application.TransactionRecorded;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Schedules the durable MOVE_RECEIPT job that files a manually entered receipt into its tax-year
 * folder. It runs synchronously inside the ledger's create transaction, so the job exists exactly
 * when the transaction commits; the job relay moves the receipt and retries a failed move.
 */
@Component
@RequiredArgsConstructor
public class ReceiptFilingListener {

  private final BackgroundJobService backgroundJobs;

  @EventListener
  public void on(TransactionRecorded event) {
    backgroundJobs.scheduleReceiptMove(
        event.transactionId(), event.attachmentExternalId(), event.date().getYear());
  }
}
