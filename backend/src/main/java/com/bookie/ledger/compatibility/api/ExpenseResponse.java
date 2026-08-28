package com.bookie.ledger.compatibility.api;

import com.bookie.catalog.activity.api.FinancialActivityResponse;
import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.catalog.counterparty.api.PayerRefResponse;
import com.bookie.catalog.property.api.PropertyRefResponse;
import com.bookie.compatibility.api.LegacyFinancialCategoryMapper;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResponse(
    Long id,
    BigDecimal amount,
    String description,
    LocalDate date,
    String category,
    FinancialCategoryResponse financialCategory,
    PropertyRefResponse property,
    ExpenseSource sourceType,
    String sourceId,
    PayerRefResponse payer,
    FinancialActivityResponse activity,
    String receiptOneDriveId,
    String receiptFileName) {

  public static ExpenseResponse from(Expense expense) {
    if (expense == null) {
      return null;
    }
    return new ExpenseResponse(
        expense.getId(),
        expense.getAmount(),
        expense.getDescription(),
        expense.getDate(),
        expense.getCategory() != null ? expense.getCategory().name() : null,
        LegacyFinancialCategoryMapper.toResponse(expense.getFinancialCategory()),
        PropertyRefResponse.from(expense.getProperty()),
        expense.getSourceType(),
        expense.getSourceId(),
        PayerRefResponse.from(expense.getPayer()),
        FinancialActivityResponse.from(expense.getActivity()),
        expense.getReceiptOneDriveId(),
        expense.getReceiptFileName());
  }
}
