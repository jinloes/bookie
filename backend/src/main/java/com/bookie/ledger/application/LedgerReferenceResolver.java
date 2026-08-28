package com.bookie.ledger.application;

import java.time.LocalDate;

public interface LedgerReferenceResolver {

  ResolvedLedgerReferences resolveFromLegacy(
      Long activityId, Long legacyCategoryId, Long legacyCounterpartyId);

  ResolvedLegacyReferences resolveForUnified(
      Long activityId, Long neutralCategoryId, Long counterpartyId, LocalDate effectiveOn);
}
