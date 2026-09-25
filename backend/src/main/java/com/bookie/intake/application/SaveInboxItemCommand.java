package com.bookie.intake.application;

import com.bookie.catalog.category.domain.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record SaveInboxItemCommand(
    TransactionDirection direction,
    BigDecimal amount,
    LocalDate date,
    String description,
    Long activityId,
    Long neutralCategoryId,
    Long counterpartyId,
    String sourceLabel) {}
