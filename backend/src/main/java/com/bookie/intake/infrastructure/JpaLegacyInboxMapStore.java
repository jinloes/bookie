package com.bookie.intake.infrastructure;

import com.bookie.intake.application.LegacyInboxMapStore;
import com.bookie.intake.domain.LegacyInboxMap;
import com.bookie.intake.domain.LegacyPendingKey;
import com.bookie.intake.domain.LegacyPendingTable;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaLegacyInboxMapStore implements LegacyInboxMapStore {

  private final LegacyInboxMapRepository repository;

  @Override
  public LegacyInboxMap save(LegacyInboxMap legacyInboxMap) {
    return repository.save(legacyInboxMap);
  }

  @Override
  public Optional<LegacyInboxMap> findByKey(LegacyPendingKey key) {
    return repository.findById(key);
  }

  @Override
  public List<LegacyInboxMap> findActiveByTable(LegacyPendingTable table) {
    return repository.findActiveByTable(table);
  }
}
