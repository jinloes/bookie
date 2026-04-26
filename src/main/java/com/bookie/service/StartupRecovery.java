package com.bookie.service;

import com.bookie.model.PendingExpenseStatus;
import com.bookie.repository.PendingExpenseRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resets any {@code PROCESSING} pending expenses to {@code FAILED} on startup. Items stuck in
 * {@code PROCESSING} indicate the async parse task was interrupted mid-flight (e.g. JVM killed
 * before the 10 s daemon thread timeout). Users can retry them via the UI.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRecovery {

  private final PendingExpenseRepository pendingExpenseRepository;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void resetStuckProcessing() {
    List<com.bookie.model.PendingExpense> stuck =
        pendingExpenseRepository.findByStatus(PendingExpenseStatus.PROCESSING);
    if (stuck.isEmpty()) {
      return;
    }
    log.warn(
        "StartupRecovery: resetting {} PROCESSING item(s) to FAILED — they can be retried",
        stuck.size());
    stuck.forEach(
        p -> {
          p.setStatus(PendingExpenseStatus.FAILED);
          p.setErrorMessage("Processing was interrupted by a server restart");
        });
    pendingExpenseRepository.saveAll(stuck);
  }
}
