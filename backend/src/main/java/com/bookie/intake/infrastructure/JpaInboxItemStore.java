package com.bookie.intake.infrastructure;

import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxItem;
import com.bookie.model.ExpenseSource;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaInboxItemStore implements InboxItemStore {

  private final InboxItemRepository repository;

  @Override
  public InboxItem save(InboxItem item) {
    return repository.save(item);
  }

  @Override
  public Optional<InboxItem> findById(Long id) {
    return repository.findById(id);
  }

  @Override
  public Optional<InboxItem> findBySourceIdentity(ExpenseSource origin, String legacySourceId) {
    return repository.findByOriginAndLegacySourceId(origin, legacySourceId);
  }

  @Override
  public List<InboxItem> findAll() {
    return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
  }
}
