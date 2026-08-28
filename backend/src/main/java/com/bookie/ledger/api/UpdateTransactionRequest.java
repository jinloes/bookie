package com.bookie.ledger.api;

import com.bookie.ledger.application.LedgerTransactionInput;
import com.bookie.model.ExpenseSource;
import com.bookie.model.TransactionDirection;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTransactionRequest(
    @NotNull Long version,
    @NotNull @Positive @Digits(integer = 36, fraction = 2) BigDecimal amount,
    @NotNull TransactionDirection direction,
    @NotNull LocalDate date,
    @NotBlank String description,
    @NotNull Long activityId,
    @NotNull Long neutralCategoryId,
    Long counterpartyId,
    ExpenseSource origin,
    String externalId,
    String sourceLabel,
    String attachmentExternalId,
    String attachmentFileName,
    String attachmentSha256) {

  LedgerTransactionInput toInput() {
    return LedgerTransactionInput.builder()
        .amount(amount)
        .direction(direction)
        .date(date)
        .description(description)
        .activityId(activityId)
        .neutralCategoryId(neutralCategoryId)
        .counterpartyId(counterpartyId)
        .origin(origin == null ? null : origin.name())
        .externalId(externalId)
        .sourceLabel(sourceLabel)
        .attachmentStorageProvider(
            attachmentExternalId == null && attachmentFileName == null && attachmentSha256 == null
                ? null
                : "ONEDRIVE")
        .attachmentExternalId(attachmentExternalId)
        .attachmentFileName(attachmentFileName)
        .attachmentSha256(attachmentSha256)
        .build();
  }
}
