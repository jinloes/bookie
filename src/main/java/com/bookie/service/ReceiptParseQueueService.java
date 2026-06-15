package com.bookie.service;

import com.bookie.model.ExpenseSource;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Parses receipt files asynchronously so the HTTP request returns immediately. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptParseQueueService {

  private static final String DEFAULT_RECEIPT_SUBJECT = "Vendor Receipt / Invoice";

  private final ReceiptService receiptService;
  private final DocumentTextExtractorService pdfExtractorService;
  private final EmailParserService emailParserService;
  private final ParseQueueSupport parseQueueSupport;

  @Async
  public void processReceipt(Long pendingId, String itemId) {
    parseQueueSupport.run(
        pendingId,
        ExpenseSource.RECEIPT,
        () -> {
          String receiptName = receiptService.getReceiptName(itemId);
          String subject = StringUtils.defaultIfBlank(receiptName, DEFAULT_RECEIPT_SUBJECT);
          byte[] pdfBytes;
          try (InputStream stream = receiptService.getReceiptContent(itemId)) {
            pdfBytes = stream != null ? stream.readAllBytes() : new byte[0];
          }
          String text = pdfExtractorService.extractText(pdfBytes, receiptName);
          return emailParserService.suggestFromEmail(subject, text, null);
        });
  }
}
