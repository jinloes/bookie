package com.bookie.intake.application;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.InboxItem;
import java.util.List;
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

  @Transactional(readOnly = true)
  public InboxItem findById(Long id) {
    return inboxItemStore
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inbox item not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<BackgroundJob> findJobs(Long inboxItemId) {
    findById(inboxItemId);
    return backgroundJobService.findByInboxItemId(inboxItemId);
  }
}
