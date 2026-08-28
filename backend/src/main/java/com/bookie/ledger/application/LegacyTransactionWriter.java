package com.bookie.ledger.application;

import com.bookie.ledger.domain.LegacyTransactionKey;

public interface LegacyTransactionWriter {

  LegacyTransactionSnapshot create(
      LedgerTransactionInput input, ResolvedLegacyReferences references);

  LegacyTransactionSnapshot update(
      LegacyTransactionKey key, LedgerTransactionInput input, ResolvedLegacyReferences references);

  void delete(LegacyTransactionKey key);
}
