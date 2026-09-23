package com.bookie.intake.domain;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Clock;
import java.time.LocalDateTime;

public class IntakeAuditListener {

  private final Clock clock;

  public IntakeAuditListener(Clock clock) {
    this.clock = clock;
  }

  @PrePersist
  public void initializeAuditFields(Object entity) {
    LocalDateTime now = LocalDateTime.now(clock);
    if (entity instanceof BackgroundJob backgroundJob) {
      backgroundJob.initializeAuditFields(now);
    } else if (entity instanceof InboxItem inboxItem) {
      inboxItem.initializeAuditFields(now);
    }
  }

  @PreUpdate
  public void touch(Object entity) {
    LocalDateTime now = LocalDateTime.now(clock);
    if (entity instanceof BackgroundJob backgroundJob) {
      backgroundJob.touch(now);
    } else if (entity instanceof InboxItem inboxItem) {
      inboxItem.touch(now);
    }
  }
}
