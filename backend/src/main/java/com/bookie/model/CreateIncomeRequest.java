package com.bookie.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateIncomeRequest(
    @NotNull BigDecimal amount,
    @NotBlank String description,
    @NotNull LocalDate date,
    String source,
    Long propertyId,
    Long payerId,
    ExpenseSource sourceType,
    String receiptOneDriveId,
    String receiptFileName,
    Long activityId,
    Long categoryId) {

  public CreateIncomeRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      String source,
      Long propertyId,
      Long payerId,
      ExpenseSource sourceType,
      String receiptOneDriveId,
      String receiptFileName,
      Long activityId) {
    this(
        amount,
        description,
        date,
        source,
        propertyId,
        payerId,
        sourceType,
        receiptOneDriveId,
        receiptFileName,
        activityId,
        null);
  }

  public CreateIncomeRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      String source,
      Long propertyId,
      Long payerId,
      ExpenseSource sourceType,
      String receiptOneDriveId,
      String receiptFileName) {
    this(
        amount,
        description,
        date,
        source,
        propertyId,
        payerId,
        sourceType,
        receiptOneDriveId,
        receiptFileName,
        null,
        null);
  }
}
