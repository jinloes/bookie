package com.bookie.ledger.compatibility.api;

import com.bookie.catalog.activity.api.FinancialActivityResponse;
import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.catalog.counterparty.api.PayerRefResponse;
import com.bookie.catalog.property.api.PropertyRefResponse;
import com.bookie.compatibility.api.LegacyFinancialCategoryMapper;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeResponse(
    Long id,
    BigDecimal amount,
    String description,
    LocalDate date,
    String source,
    String sourceId,
    ExpenseSource sourceType,
    String receiptOneDriveId,
    String receiptFileName,
    PropertyRefResponse property,
    PayerRefResponse payer,
    FinancialActivityResponse activity,
    FinancialCategoryResponse financialCategory) {

  public static IncomeResponse from(Income income) {
    if (income == null) {
      return null;
    }
    return new IncomeResponse(
        income.getId(),
        income.getAmount(),
        income.getDescription(),
        income.getDate(),
        income.getSource(),
        income.getSourceId(),
        income.getSourceType(),
        income.getReceiptOneDriveId(),
        income.getReceiptFileName(),
        PropertyRefResponse.from(income.getProperty()),
        PayerRefResponse.from(income.getPayer()),
        FinancialActivityResponse.from(income.getActivity()),
        LegacyFinancialCategoryMapper.toResponse(income.getFinancialCategory()));
  }
}
