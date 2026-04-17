package com.bookie.service;

import com.bookie.model.EmailSuggestion;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseSource;
import java.util.Map;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Shared scaffolding for async parse jobs. Manages the parse-session lifecycle (thread-local
 * context, markReady/markFailed, SSE emit) so queue services only need to supply the
 * source-specific parsing logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParseQueueSupport {

  private final ParseSessionContext parseSessionContext;
  private final PendingExpenseService pendingExpenseService;
  private final SseService sseService;

  /**
   * Runs {@code task}, then marks the pending item ready and emits an SSE event. On failure, marks
   * it failed and emits a failure event. Always clears the parse session context before and after.
   */
  public void run(Long pendingId, ExpenseSource sourceType, Callable<EmailSuggestion> task) {
    parseSessionContext.clear();
    try {
      EmailSuggestion suggestion = task.call();
      pendingExpenseService.markReady(
          pendingId, suggestion, parseSessionContext.getUnrecognizedAliases());
      EmailType emailType =
          suggestion.emailType() != null ? suggestion.emailType() : EmailType.EXPENSE;
      sseService.emit(
          "pending-updated",
          Map.of(
              "id",
              pendingId,
              "status",
              "READY",
              "emailType",
              emailType.name(),
              "sourceType",
              sourceType.name()));
    } catch (Exception e) {
      log.error("Failed to parse {} for pending {}", sourceType, pendingId, e);
      pendingExpenseService.markFailed(pendingId, e.getMessage());
      sseService.emit("pending-updated", Map.of("id", pendingId, "status", "FAILED"));
    } finally {
      parseSessionContext.clear();
    }
  }
}
