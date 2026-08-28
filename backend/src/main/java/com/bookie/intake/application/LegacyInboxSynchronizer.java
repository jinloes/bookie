package com.bookie.intake.application;

import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.ledger.domain.LegacyTransactionKey;

public interface LegacyInboxSynchronizer {

  void created(LegacyPendingKey key, LegacyInboxSnapshot snapshot, boolean parsingRequired);

  void ready(LegacyPendingKey key, LegacyInboxSnapshot snapshot);

  void failed(LegacyPendingKey key, LegacyInboxSnapshot snapshot);

  void retryQueued(LegacyPendingKey key, LegacyInboxSnapshot snapshot);

  void savePending(LegacyPendingKey key, LegacyInboxSnapshot snapshot);

  void saved(
      LegacyPendingKey key,
      LegacyInboxSnapshot snapshot,
      LegacyTransactionKey legacyTransactionKey);

  void dismissed(LegacyPendingKey key, LegacyInboxSnapshot snapshot);
}
