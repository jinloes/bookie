package com.bookie.service;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.ExpenseSource;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Runs email parsing asynchronously so the HTTP request returns immediately. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailParseQueueService {

  private final OutlookService outlookService;
  private final EmailParserService emailParserService;
  private final PendingExpenseService pendingExpenseService;
  private final PropertyHistoryService propertyHistoryService;
  private final ParseSessionContext parseSessionContext;
  private final SseService sseService;

  @Async
  public void processEmail(Long pendingId, String messageId) {
    parseSessionContext.clear();
    try {
      OutlookService.MessageContent message = outlookService.fetchMessageBody(messageId);
      EmailSuggestion suggestion =
          emailParserService.suggestFromEmail(
              message.subject(), message.body(), message.receivedDate());
      propertyHistoryService.storeKeywords(messageId, suggestion.keywords());
      List<String> unrecognizedAliases = parseSessionContext.getUnrecognizedAliases();
      pendingExpenseService.markReady(
          pendingId,
          suggestion.toBuilder()
              .sourceType(ExpenseSource.OUTLOOK_EMAIL)
              .sourceId(messageId)
              .build(),
          unrecognizedAliases);
      sseService.emit("pending-updated", Map.of("id", pendingId, "status", "READY"));
    } catch (Exception e) {
      log.error("Failed to parse email {} for pending {}", messageId, pendingId, e);
      pendingExpenseService.markFailed(pendingId, e.getMessage());
      sseService.emit("pending-updated", Map.of("id", pendingId, "status", "FAILED"));
    } finally {
      parseSessionContext.clear();
    }
  }
}
