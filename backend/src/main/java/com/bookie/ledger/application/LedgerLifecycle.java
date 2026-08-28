package com.bookie.ledger.application;

public interface LedgerLifecycle {

  void reassignActivity(
      Long activityId,
      Long replacementActivityId,
      Long incomeLegacyCategoryId,
      Long expenseLegacyCategoryId);

  void detachCounterparty(Long counterpartyId);

  long countActivityReferences(Long activityId);

  long countCounterpartyReferences(Long counterpartyId);
}
