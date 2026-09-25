package com.bookie.intake.infrastructure;

import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaInboxItemStore implements InboxItemStore {

  private static final Set<InboxState> TERMINAL_STATES =
      EnumSet.of(InboxState.SAVED, InboxState.DISMISSED);

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

  @Override
  public List<InboxItem> findActive() {
    return repository.findByStateNotInOrderByCreatedAtDescIdDesc(TERMINAL_STATES);
  }

  @Override
  public List<InboxItem> findActiveForOutlookMessages(Collection<String> messageIds) {
    if (messageIds.isEmpty()) {
      return List.of();
    }
    return repository.findActiveForOutlookMessages(messageIds, TERMINAL_STATES);
  }

  @Override
  public void reassignActivity(
      Long activityId, Long replacementActivityId, Long incomeCategoryId, Long expenseCategoryId) {
    repository.reassignClassifiedActivity(
        activityId, replacementActivityId, incomeCategoryId, expenseCategoryId);
    repository.clearConfiguredActivity(activityId);
  }

  @Override
  public void detachProperty(Long propertyId) {
    repository.clearProperty(propertyId);
  }

  @Override
  public void detachCounterparty(Long counterpartyId) {
    repository.clearCounterparty(counterpartyId);
  }

  @Override
  public long countActivityReferences(Long activityId) {
    return repository.countActivityReferences(activityId);
  }

  @Override
  public long countPropertyReferences(Long propertyId) {
    return repository.countByLegacyPropertyId(propertyId);
  }

  @Override
  public long countCounterpartyReferences(Long counterpartyId) {
    return repository.countByLegacyCounterpartyId(counterpartyId);
  }
}
