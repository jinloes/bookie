package com.bookie.intake.api;

import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.model.ExpenseSource;
import com.bookie.model.TransactionDirection;
import java.time.LocalDateTime;

public record InboxItemResponse(
    Long id,
    ExpenseSource origin,
    String legacySourceId,
    String immutableSourceId,
    InboxState state,
    ExternalSyncState externalSyncState,
    TransactionDirection proposedDirection,
    Long financialTransactionId,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long version) {

  public static InboxItemResponse from(InboxItem item) {
    return new InboxItemResponse(
        item.getId(),
        item.getOrigin(),
        item.getLegacySourceId(),
        item.getImmutableSourceId(),
        item.getState(),
        item.getExternalSyncState(),
        item.getProposedDirection(),
        item.getFinancialTransactionId(),
        item.getErrorMessage(),
        item.getCreatedAt(),
        item.getUpdatedAt(),
        item.getVersion());
  }
}
