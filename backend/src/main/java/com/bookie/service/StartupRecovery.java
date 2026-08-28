package com.bookie.service;

import com.bookie.intake.application.BackgroundJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases expired durable job leases on startup. The item remains queued and can resume instead of
 * being rewritten as a generic failure after every process restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRecovery {

  private final BackgroundJobService backgroundJobService;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void recoverExpiredLeases() {
    int recovered = backgroundJobService.recoverExpiredLeases();
    if (recovered == 0) {
      return;
    }
    log.warn("StartupRecovery: released {} expired background job lease(s)", recovered);
  }
}
