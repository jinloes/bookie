package com.bookie.service;

import com.bookie.model.ExpenseSource;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Parses receipt PDFs asynchronously so the HTTP request returns immediately. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptParseQueueService {

  private final ReceiptService receiptService;
  private final PdfExtractorService pdfExtractorService;
  private final EmailParserService emailParserService;
  private final ParseQueueSupport parseQueueSupport;

  @Async
  public void processReceipt(Long pendingId, String itemId) {
    parseQueueSupport.run(
        pendingId,
        ExpenseSource.RECEIPT,
        () -> {
          byte[] pdfBytes;
          try (InputStream stream = receiptService.getReceiptContent(itemId)) {
            pdfBytes = stream != null ? stream.readAllBytes() : new byte[0];
          }
          String text = pdfExtractorService.extractText(pdfBytes);
          return emailParserService.suggestFromEmail("Vendor Receipt / Invoice", text, null);
        });
  }
}
