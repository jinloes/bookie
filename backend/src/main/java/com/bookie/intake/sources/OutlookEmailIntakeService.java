package com.bookie.intake.sources;

import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.sources.InboxItemCreationService.FindOrCreateResult;
import com.bookie.intake.sources.InboxItemCreationService.IntakeSource;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OutlookEmailIntakeService {

  private final OutlookService outlookService;
  private final InboxItemCreationService inboxItemCreationService;
  private final InboxItemStore inboxItemStore;

  public QueueResult queue(String messageId, Long configuredActivityId) {
    List<OutlookService.IntakeTarget> targets = outlookService.discoverIntakeTargets(messageId);
    List<InboxItem> items = new ArrayList<>();
    int queuedCount = 0;
    for (OutlookService.IntakeTarget target : targets) {
      Optional<InboxItem> existing =
          inboxItemStore.findBySourceIdentity(ExpenseSource.OUTLOOK_EMAIL, target.sourceId());
      if (existing.isPresent() && isTerminal(existing.get())) {
        continue;
      }
      if (existing.isPresent() && existing.get().getState() != InboxState.FAILED) {
        items.add(existing.get());
        continue;
      }
      try {
        FindOrCreateResult result =
            inboxItemCreationService.findOrCreate(
                new IntakeSource(
                    target.sourceId(),
                    ExpenseSource.OUTLOOK_EMAIL,
                    target.subject(),
                    configuredActivityId,
                    target.outlookMessageId(),
                    target.attachmentId(),
                    target.attachmentName()));
        items.add(result.item());
        if (!result.alreadyProcessing()) {
          queuedCount++;
        }
      } catch (ResponseStatusException conflict) {
        if (conflict.getStatusCode() != HttpStatus.CONFLICT) {
          throw conflict;
        }
      }
    }
    return new QueueResult(List.copyOf(items), queuedCount);
  }

  private boolean isTerminal(InboxItem item) {
    return item.getState() == InboxState.SAVED || item.getState() == InboxState.DISMISSED;
  }

  public record QueueResult(List<InboxItem> items, int queuedCount) {

    public List<Long> ids() {
      return items.stream().map(InboxItem::getId).toList();
    }

    public InboxItem first() {
      return items.stream()
          .findFirst()
          .orElseThrow(
              () ->
                  new ResponseStatusException(
                      HttpStatus.CONFLICT, "Every attachment from this email is already complete"));
    }
  }
}
