package com.bookie.intake.sources;

import static org.mockito.Mockito.verify;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.ledger.application.TransactionRecorded;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptFilingListenerTest {

  @Mock private BackgroundJobService backgroundJobs;

  @InjectMocks private ReceiptFilingListener listener;

  @Nested
  class On {

    @Test
    void schedulesTheReceiptMoveIntoTheTransactionDatesYear() {
      listener.on(new TransactionRecorded(41L, "RECEIPT", "drive-41", LocalDate.of(2025, 12, 31)));

      verify(backgroundJobs).scheduleReceiptMove(41L, "drive-41", 2025);
    }
  }
}
