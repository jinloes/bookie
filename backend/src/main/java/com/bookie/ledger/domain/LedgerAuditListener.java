package com.bookie.ledger.domain;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Clock;
import java.time.LocalDateTime;

public class LedgerAuditListener {

  private final Clock clock;

  public LedgerAuditListener(Clock clock) {
    this.clock = clock;
  }

  @PrePersist
  public void initializeAuditFields(FinancialTransaction transaction) {
    transaction.initializeAuditFields(LocalDateTime.now(clock));
  }

  @PreUpdate
  public void touch(FinancialTransaction transaction) {
    transaction.touch(LocalDateTime.now(clock));
  }
}
