package com.bookie.service;

import static org.mockito.Mockito.verify;

import com.bookie.intake.application.DurableBackgroundJobWorker;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptParseQueueServiceTest {

  @Mock private DurableBackgroundJobWorker backgroundJobWorker;

  @InjectMocks private ReceiptParseQueueService service;

  @Nested
  class ProcessReceipt {

    @Test
    void runsTheAlreadyPersistedDurableJob() {
      service.processReceipt(10L, "item-1");

      verify(backgroundJobWorker)
          .runAvailableForLegacy(
              new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, 10L),
              BackgroundJobType.PARSE_RECEIPT);
    }

    @Test
    void compatibilitySourceArgumentDoesNotReplaceTheDurableIdentity() {
      service.processReceipt(5L, "item-2");

      verify(backgroundJobWorker)
          .runAvailableForLegacy(
              new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, 5L),
              BackgroundJobType.PARSE_RECEIPT);
    }
  }
}
