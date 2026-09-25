package com.bookie.ledger.application;

import java.time.LocalDate;

public interface LedgerReferenceResolver {

  /**
   * Resolves a transaction's activity, neutral category and counterparty, plus the financial
   * category the neutral category maps to for that activity on {@code effectiveOn}.
   */
  ResolvedTransactionReferences resolve(
      Long activityId, Long neutralCategoryId, Long counterpartyId, LocalDate effectiveOn);

  /** Resolves an activity and the neutral category a retained financial category maps to. */
  ResolvedTransactionReferences resolveFinancialCategory(Long activityId, Long financialCategoryId);
}
