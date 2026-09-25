package com.bookie.intake.sources;

import com.bookie.ledger.application.TransactionRecorded;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Files a manually entered receipt into its tax-year folder after the ledger transaction commits. A
 * failed move throws, leaving the event publication incomplete for {@link
 * EventPublicationResubmitter} to retry until it completes.
 */
@Component
@RequiredArgsConstructor
public class ReceiptFilingListener {

  private final ReceiptService receiptService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(TransactionRecorded event) {
    receiptService.moveTaxesFolderForJob(event.attachmentExternalId(), event.date().getYear());
  }
}
