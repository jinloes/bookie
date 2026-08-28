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
class EmailParseQueueServiceTest {

  @Mock private DurableBackgroundJobWorker backgroundJobWorker;

  @InjectMocks private EmailParseQueueService service;

  @Nested
  class ProcessEmail {

    @Test
    void runsTheAlreadyPersistedDurableJob() {
      service.processEmail(10L, "msg-1");

      verify(backgroundJobWorker)
          .runAvailableForLegacy(
              new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, 10L),
              BackgroundJobType.PARSE_OUTLOOK);
    }

    @Test
    void compatibilityArgumentsDoNotReplaceTheDurableIdentity() {
      service.processEmail(11L, "msg-pay", 42L);

      verify(backgroundJobWorker)
          .runAvailableForLegacy(
              new LegacyPendingKey(LegacyPendingTable.PENDING_EXPENSES, 11L),
              BackgroundJobType.PARSE_OUTLOOK);
    }
  }
}
