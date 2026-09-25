package com.bookie.intake.sources;

import com.bookie.intake.application.InboxEventStream;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.application.JobExecutionException;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.ParseSessionContext;
import com.bookie.integrations.IntegrationException;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared scaffolding for async parse jobs. Manages the parse-session lifecycle (thread-local
 * context, markReady/markFailed, inbox SSE notification) so queue services only need to supply the
 * source-specific parsing logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParseQueueSupport {

  private final ParseSessionContext parseSessionContext;
  private final InboxItemCreationService inboxItemCreationService;
  private final InboxItemStore inboxItemStore;
  private final InboxEventStream inboxEventStream;

  /**
   * Runs {@code task}, then marks the inbox item ready and notifies inbox subscribers. On failure,
   * marks it failed and notifies subscribers. Always clears the parse session context before and
   * after.
   */
  public void run(Long inboxItemId, ExpenseSource sourceType, Callable<EmailSuggestion> task) {
    parseSessionContext.clear();
    try {
      EmailSuggestion suggestion = task.call();
      inboxItemCreationService.markReady(
          inboxItemId, suggestion, parseSessionContext.getUnrecognizedAliases());
      publishInboxUpdate(inboxItemId);
    } catch (Exception e) {
      log.error("Failed to parse {} for inbox item {}", sourceType, inboxItemId, e);
      inboxItemCreationService.markFailed(inboxItemId, e.getMessage());
      publishInboxUpdate(inboxItemId);
      if (e instanceof JobExecutionException jobFailure) {
        throw jobFailure;
      }
      if (e instanceof IntegrationException integrationFailure) {
        throw integrationFailure;
      }
      if (e instanceof ResponseStatusException responseFailure) {
        throw responseFailure;
      }
      throw JobExecutionException.retryable("Could not parse intake item", e);
    } finally {
      parseSessionContext.clear();
    }
  }

  private void publishInboxUpdate(Long inboxItemId) {
    inboxItemStore.findById(inboxItemId).ifPresent(inboxEventStream::inboxUpdated);
  }
}
