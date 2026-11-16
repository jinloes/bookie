package com.bookie.intake.application;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;

/** Published whenever an {@link InboxItem} is persisted so SSE subscribers hear after commit. */
public record InboxItemUpdated(
    Long id, InboxState state, ExpenseSource origin, TransactionDirection proposedDirection) {

  public static InboxItemUpdated from(InboxItem item) {
    return new InboxItemUpdated(
        item.getId(), item.getState(), item.getOrigin(), item.getProposedDirection());
  }
}
