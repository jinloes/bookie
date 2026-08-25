package com.bookie.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateExpenseRequest(
    @NotNull BigDecimal amount,
    @NotBlank String description,
    @NotNull LocalDate date,
    ExpenseCategory category,
    Long propertyId,
    Long payerId,
    String receiptOneDriveId,
    String receiptFileName,
    Long activityId,
    Long categoryId) {

  public UpdateExpenseRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      ExpenseCategory category,
      Long propertyId,
      Long payerId,
      String receiptOneDriveId,
      String receiptFileName,
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
        activityId,
        null);
  }

  public UpdateExpenseRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      ExpenseCategory category,
      Long propertyId,
      Long payerId,
      String receiptOneDriveId,
      String receiptFileName) {
    this(
        amount,
        description,
        date,
        category,
        propertyId,
        payerId,
        receiptOneDriveId,
        receiptFileName,
        null,
        null);
  }
}
