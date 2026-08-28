package com.bookie.service;

import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Runs email parsing asynchronously so the HTTP request returns immediately. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailParseQueueService {

  private final DurableBackgroundJobWorker backgroundJobWorker;

  @Async
  public void processEmail(Long pendingId, String messageId) {
    processEmail(pendingId, messageId, null);
  }

  @Async
  public void processEmail(Long pendingId, String messageId, Long configuredActivityId) {
    backgroundJobWorker.runAvailableForLegacy(
        new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, pendingId),
        BackgroundJobType.PARSE_OUTLOOK);
  }
}
