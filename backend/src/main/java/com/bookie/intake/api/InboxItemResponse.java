package com.bookie.intake.api;

import com.bookie.catalog.activity.api.FinancialActivityResponse;
import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.intake.domain.ExternalSyncState;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record InboxItemResponse(
    Long id,
    ExpenseSource origin,
    String sourceId,
    String immutableSourceId,
    InboxState state,
    ExternalSyncState externalSyncState,
    TransactionDirection proposedDirection,
    String subject,
    BigDecimal amount,
    String description,
    LocalDate date,
    String proposedCategory,
    String propertyName,
    String counterpartyName,
    String sourceLabel,
    FinancialActivityResponse activity,
    FinancialCategoryResponse category,
    Long counterpartyId,
    boolean classificationAmbiguous,
    String receiptExternalId,
    String receiptFileName,
    Long financialTransactionId,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long version) {}
