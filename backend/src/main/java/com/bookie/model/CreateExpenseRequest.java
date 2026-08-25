package com.bookie.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExpenseRequest(
    @NotNull BigDecimal amount,
    @NotBlank String description,
    @NotNull LocalDate date,
    ExpenseCategory category,
    Long propertyId,
    Long payerId,
    String receiptOneDriveId,
    String receiptFileName,
    ExpenseSource sourceType,
    Long activityId,
    Long categoryId) {

  public CreateExpenseRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      ExpenseCategory category,
      Long propertyId,
      Long payerId,
      String receiptOneDriveId,
      String receiptFileName,
      ExpenseSource sourceType,
      Long activityId) {
    this(
        amount,
        description,
        date,
        category,
        propertyId,
        payerId,
        receiptOneDriveId,
        receiptFileName,
        sourceType,
        activityId,
        null);
  }

  public CreateExpenseRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      ExpenseCategory category,
      Long propertyId,
      Long payerId,
      String receiptOneDriveId,
      String receiptFileName,
      ExpenseSource sourceType) {
    this(
        amount,
        description,
        date,
        category,
        propertyId,
        payerId,
        receiptOneDriveId,
        receiptFileName,
        sourceType,
        null,
        null);
  }
}
