package com.bookie.ledger.application;

import java.util.Collection;
import java.util.Set;

/** Reads source identities recorded on active ledger transactions. */
public interface LedgerImportReferenceQuery {

  /**
   * Returns the subset of {@code parentExternalIds} that already own an active ledger transaction
   * imported from {@code origin}.
   */
  Set<String> findSavedParentExternalIds(String origin, Collection<String> parentExternalIds);
}
