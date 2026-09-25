package com.bookie.ledger.application;

import com.bookie.catalog.category.domain.TransactionDirection;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface LedgerImportReferenceQuery {

  /**
   * Returns the subset of {@code parentExternalIds} that already own an active ledger transaction
   * imported from {@code origin}.
   */
  Set<String> findSavedParentExternalIds(String origin, Collection<String> parentExternalIds);

  /** Returns whether an active transaction was imported from this exact source identity. */
  boolean existsActive(String origin, String externalId);

  /**
   * Returns the active transactions that attach, or were imported from, any of the given external
   * IDs (for example OneDrive item IDs), one entry per matching ID and transaction.
   */
  List<LinkedTransaction> findActiveLinks(Collection<String> externalIds);

  record LinkedTransaction(
      String externalId, Long transactionId, Long version, TransactionDirection direction) {}
}
