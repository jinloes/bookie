package com.bookie.ledger.application;

public interface LedgerLifecycle {

  void reassignActivity(
      Long activityId,
      Long replacementActivityId,
      Long incomeNeutralCategoryId,
      Long expenseNeutralCategoryId);

  void detachCounterparty(Long counterpartyId);

  long countActivityReferences(Long activityId);

  long countCounterpartyReferences(Long counterpartyId);
}
