package com.bookie.ledger.application;

import java.time.LocalDate;

public interface LedgerReferenceResolver {

  /**
   * Resolves a transaction's activity, neutral category and counterparty, verifying that the
   * category has an active reporting mapping for that activity on {@code effectiveOn}.
   */
  ResolvedTransactionReferences resolve(
      Long activityId, Long neutralCategoryId, Long counterpartyId, LocalDate effectiveOn);

  /** Resolves an activity and neutral category during administrative activity reassignment. */
  ResolvedTransactionReferences resolveReassignment(Long activityId, Long neutralCategoryId);
}
