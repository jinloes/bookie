package com.bookie.intake.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Kicks the durable job relay once the transaction that scheduled a job has committed. Not an
 * {@code @ApplicationModuleListener}: its REQUIRES_NEW transaction would wrap the relay, delaying
 * the bind commit past the JobRunr enqueue. A lost kick is recovered by the scheduled relay.
 */
@Component
@RequiredArgsConstructor
public class IntakeJobKickoffListener {

  private final DurableBackgroundJobWorker worker;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(InboxItemSaved event) {
    worker.runAvailableJob(null);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(InboxItemQueued event) {
    worker.runAvailableJob(null);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(BackgroundJobQueued event) {
    worker.runAvailableJob(null);
  }
}
