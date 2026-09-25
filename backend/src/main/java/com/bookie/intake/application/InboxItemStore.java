package com.bookie.intake.application;

import com.bookie.intake.domain.InboxItem;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InboxItemStore {

  InboxItem save(InboxItem item);

  Optional<InboxItem> findById(Long id);

  Optional<InboxItem> findBySourceIdentity(ExpenseSource origin, String legacySourceId);

  List<InboxItem> findAll();

  /** Returns items that are neither saved nor dismissed, newest first. */
  List<InboxItem> findActive();

  /** Returns active Outlook items whose recorded source message is one of {@code messageIds}. */
  List<InboxItem> findActiveForOutlookMessages(Collection<String> messageIds);

  /**
   * Moves every item classified to a retired activity onto the replacement, using the income or
   * expense category by proposed direction, and clears it as a configured activity.
   */
  void reassignActivity(
      Long activityId, Long replacementActivityId, Long incomeCategoryId, Long expenseCategoryId);

  void detachProperty(Long propertyId);

  void detachCounterparty(Long counterpartyId);

  long countActivityReferences(Long activityId);

  long countPropertyReferences(Long propertyId);

  long countCounterpartyReferences(Long counterpartyId);
}
