package com.bookie.intake.sources;

import com.bookie.intake.application.JobExecutionException;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.integrations.IntegrationException;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared scaffolding for async parse jobs. Manages the parse lifecycle (markReady/markFailed) while
 * {@link com.bookie.intake.application.InboxEventStream} broadcasts committed state changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParseQueueSupport {

  private final InboxItemCreationService inboxItemCreationService;

  /** Runs {@code task}, then marks the inbox item ready or failed. */
  public void run(Long inboxItemId, ExpenseSource sourceType, Callable<EmailSuggestion> task) {
    try {
      EmailSuggestion suggestion = task.call();
      List<String> aliases =
          List.copyOf(CollectionUtils.emptyIfNull(suggestion.unrecognizedAliases()));
      inboxItemCreationService.markReady(inboxItemId, suggestion, aliases);
    } catch (Exception e) {
      log.error("Failed to parse {} for inbox item {}", sourceType, inboxItemId, e);
      inboxItemCreationService.markFailed(inboxItemId, e.getMessage());
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
    }
  }
}
