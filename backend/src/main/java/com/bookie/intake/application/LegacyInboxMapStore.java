package com.bookie.intake.application;

import com.bookie.intake.domain.LegacyInboxMap;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import java.util.List;
import java.util.Optional;

public interface LegacyInboxMapStore {

  LegacyInboxMap save(LegacyInboxMap legacyInboxMap);

  Optional<LegacyInboxMap> findByKey(LegacyPendingKey key);

  List<LegacyInboxMap> findActiveByTable(LegacyPendingTable table);
}
