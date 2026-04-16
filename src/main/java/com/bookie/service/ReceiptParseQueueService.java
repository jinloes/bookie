package com.bookie.service;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseSource;
import java.io.InputStream;
import java.util.Map;
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
  private final PendingExpenseService pendingExpenseService;
  private final ParseSessionContext parseSessionContext;
  private final SseService sseService;

  @Async
  public void processReceipt(Long pendingId, String itemId) {
    parseSessionContext.clear();
    try {
      InputStream stream = receiptService.getReceiptContent(itemId);
      byte[] pdfBytes = stream != null ? stream.readAllBytes() : new byte[0];
      String text = pdfExtractorService.extractText(pdfBytes);

      EmailSuggestion suggestion =
          emailParserService.suggestFromEmail("Vendor Receipt / Invoice", text, null);

      pendingExpenseService.markReady(
          pendingId,
          suggestion.toBuilder().sourceType(ExpenseSource.RECEIPT).sourceId(itemId).build(),
          parseSessionContext.getUnrecognizedAliases());

      sseService.emit(
          "pending-updated",
          Map.of("id", pendingId, "status", "READY", "emailType", EmailType.EXPENSE.name()));
    } catch (Exception e) {
      log.error("Failed to parse receipt {} for pending {}", itemId, pendingId, e);
      pendingExpenseService.markFailed(pendingId, e.getMessage());
      sseService.emit("pending-updated", Map.of("id", pendingId, "status", "FAILED"));
    } finally {
      parseSessionContext.clear();
    }
  }
}
