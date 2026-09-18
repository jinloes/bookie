package com.bookie.service;

import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxState;
import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OutlookEmailIntakeService {

  private final OutlookService outlookService;
  private final PendingExpenseService pendingExpenseService;
  private final EmailParseQueueService emailParseQueueService;
  private final InboxItemStore inboxItemStore;

  public QueueResult queue(String messageId, Long configuredActivityId) {
    List<OutlookService.IntakeTarget> targets = outlookService.discoverIntakeTargets(messageId);
    List<PendingExpense> pendingItems = new ArrayList<>();
    int queuedCount = 0;
    for (OutlookService.IntakeTarget target : targets) {
      PendingExpense existing =
          pendingExpenseService.findBySourceId(target.sourceId()).orElse(null);
      if (existing != null && existing.getStatus() != PendingExpenseStatus.FAILED) {
        pendingItems.add(existing);
        continue;
      }
      if (existing == null && isTerminal(target.sourceId())) {
        continue;
      }
      try {
        PendingExpenseService.FindOrCreateResult result =
            pendingExpenseService.findOrCreate(
                target.sourceId(),
                ExpenseSource.OUTLOOK_EMAIL,
                target.subject(),
                configuredActivityId,
                target.outlookMessageId(),
                target.attachmentId(),
                target.attachmentName());
        pendingItems.add(result.pending());
        if (!result.alreadyProcessing()) {
          emailParseQueueService.processEmail(
              result.pending().getId(),
              target.outlookMessageId(),
              result.pending().getConfiguredActivityId());
          queuedCount++;
        }
      } catch (ResponseStatusException conflict) {
        if (conflict.getStatusCode() != HttpStatus.CONFLICT) {
          throw conflict;
        }
      }
    }
    return new QueueResult(List.copyOf(pendingItems), queuedCount);
  }

  private boolean isTerminal(String sourceId) {
    return inboxItemStore
        .findBySourceIdentity(ExpenseSource.OUTLOOK_EMAIL, sourceId)
        .map(item -> item.getState() == InboxState.SAVED || item.getState() == InboxState.DISMISSED)
        .orElse(false);
  }

  public record QueueResult(List<PendingExpense> pendingItems, int queuedCount) {

    public List<Long> ids() {
      return pendingItems.stream().map(PendingExpense::getId).toList();
    }

    public PendingExpense first() {
      return pendingItems.stream()
          .findFirst()
          .orElseThrow(
              () ->
                  new org.springframework.web.server.ResponseStatusException(
                      org.springframework.http.HttpStatus.CONFLICT,
                      "Every attachment from this email is already complete"));
    }
  }
}
