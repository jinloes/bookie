package com.bookie.intake.compatibility.api;

import com.bookie.catalog.activity.api.FinancialActivityResponse;
import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.compatibility.api.LegacyFinancialCategoryMapper;
import com.bookie.model.EmailType;
import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PendingExpenseResponse(
    Long id,
    String sourceId,
    ExpenseSource sourceType,
    EmailType emailType,
    String subject,
    PendingExpenseStatus status,
    BigDecimal amount,
    String description,
    LocalDate date,
    String category,
    String propertyName,
    String payerName,
    String counterpartyName,
    FinancialActivityResponse activity,
    FinancialCategoryResponse financialCategory,
    boolean classificationAmbiguous,
    String errorMessage,
    LocalDateTime createdAt) {

  public static PendingExpenseResponse from(PendingExpense pendingExpense) {
    if (pendingExpense == null) {
      return null;
    }
    return new PendingExpenseResponse(
        pendingExpense.getId(),
        pendingExpense.getSourceId(),
        pendingExpense.getSourceType(),
        pendingExpense.getEmailType(),
        pendingExpense.getSubject(),
        pendingExpense.getStatus(),
        pendingExpense.getAmount(),
        pendingExpense.getDescription(),
        pendingExpense.getDate(),
        pendingExpense.getCategory(),
        pendingExpense.getPropertyName(),
        pendingExpense.getPayerName(),
        pendingExpense.getPayerName(),
        FinancialActivityResponse.from(pendingExpense.getActivity()),
        LegacyFinancialCategoryMapper.toResponse(pendingExpense.getFinancialCategory()),
        pendingExpense.isClassificationAmbiguous(),
        pendingExpense.getErrorMessage(),
        pendingExpense.getCreatedAt());
  }
}
