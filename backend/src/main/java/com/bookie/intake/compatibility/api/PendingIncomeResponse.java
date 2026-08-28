package com.bookie.intake.compatibility.api;

import com.bookie.catalog.activity.api.FinancialActivityResponse;
import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.catalog.counterparty.api.PayerRefResponse;
import com.bookie.catalog.property.api.PropertyRefResponse;
import com.bookie.compatibility.api.LegacyFinancialCategoryMapper;
import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingIncome;
import com.bookie.model.PendingIncomeStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PendingIncomeResponse(
    Long id,
    BigDecimal amount,
    String description,
    LocalDate date,
    String source,
    String sourceId,
    ExpenseSource sourceType,
    PendingIncomeStatus status,
    LocalDateTime createdAt,
    PropertyRefResponse property,
    PayerRefResponse payer,
    FinancialActivityResponse activity,
    FinancialCategoryResponse financialCategory,
    boolean classificationAmbiguous) {

  public static PendingIncomeResponse from(PendingIncome pending) {
    if (pending == null) {
      return null;
    }
    return new PendingIncomeResponse(
        pending.getId(),
        pending.getAmount(),
        pending.getDescription(),
        pending.getDate(),
        pending.getSource(),
        pending.getSourceId(),
        pending.getSourceType(),
        pending.getStatus(),
        pending.getCreatedAt(),
        PropertyRefResponse.from(pending.getProperty()),
        PayerRefResponse.from(pending.getPayer()),
        FinancialActivityResponse.from(pending.getActivity()),
        LegacyFinancialCategoryMapper.toResponse(pending.getFinancialCategory()),
        pending.isClassificationAmbiguous());
  }
}
