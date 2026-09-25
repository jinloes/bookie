package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.InboxItem;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class InboxQueryService {

  private final InboxItemStore inboxItemStore;
  private final BackgroundJobService backgroundJobService;

  @Transactional(readOnly = true)
  public List<InboxItem> findAll() {
    return inboxItemStore.findAll();
  }

  /** Returns the review queue: every item that is neither saved nor dismissed, newest first. */
  @Transactional(readOnly = true)
  public List<InboxItem> findActive() {
    return inboxItemStore.findActive();
  }

  @Transactional(readOnly = true)
  public InboxItem findById(Long id) {
    return inboxItemStore
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inbox item not found: " + id));
  }

  @Transactional(readOnly = true)
  public Optional<InboxItem> findBySource(ExpenseSource origin, String sourceId) {
    return inboxItemStore.findBySourceIdentity(origin, sourceId);
  }

  @Transactional(readOnly = true)
  public List<BackgroundJob> findJobs(Long inboxItemId) {
    findById(inboxItemId);
    return backgroundJobService.findByInboxItemId(inboxItemId);
  }
}
