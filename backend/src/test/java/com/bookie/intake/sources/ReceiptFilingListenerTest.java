package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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

  private static final TransactionRecorded RECORDED =
      new TransactionRecorded(31L, "RECEIPT", "drive-7", LocalDate.of(2025, 12, 31));

  @Mock private ReceiptService receiptService;

  @InjectMocks private ReceiptFilingListener listener;

  @Nested
  class On {

    @Test
    void movesTheReceiptIntoTheTransactionTaxYear() {
      listener.on(RECORDED);

      verify(receiptService).moveTaxesFolderForJob("drive-7", 2025);
    }

    @Test
    void propagatesAFailedMoveSoThePublicationStaysIncomplete() {
      doThrow(new IllegalStateException("offline"))
          .when(receiptService)
          .moveTaxesFolderForJob("drive-7", 2025);

      assertThatThrownBy(() -> listener.on(RECORDED)).hasMessage("offline");
    }
  }
}
