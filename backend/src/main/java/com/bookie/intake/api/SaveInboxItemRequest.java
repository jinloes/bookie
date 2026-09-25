package com.bookie.intake.api;

import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.intake.application.SaveInboxItemCommand;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SaveInboxItemRequest(
    @NotNull TransactionDirection direction,
    @NotNull @Positive @Digits(integer = 36, fraction = 2) BigDecimal amount,
    @NotNull LocalDate date,
    @NotBlank String description,
    @NotNull Long activityId,
    @NotNull Long neutralCategoryId,
    Long counterpartyId,
    String sourceLabel) {

  SaveInboxItemCommand toCommand() {
    return SaveInboxItemCommand.builder()
        .direction(direction)
        .amount(amount)
        .date(date)
        .description(description)
        .activityId(activityId)
        .neutralCategoryId(neutralCategoryId)
        .counterpartyId(counterpartyId)
        .sourceLabel(sourceLabel)
        .build();
  }
}
