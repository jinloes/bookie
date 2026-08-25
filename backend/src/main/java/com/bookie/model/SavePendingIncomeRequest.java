package com.bookie.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SavePendingIncomeRequest(
    @NotNull BigDecimal amount,
    @NotBlank String description,
    @NotNull LocalDate date,
    String source,
    Long propertyId,
    Long activityId,
    Long categoryId) {

  public SavePendingIncomeRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      String source,
      Long propertyId,
      Long activityId) {
    this(amount, description, date, source, propertyId, activityId, null);
  }

  public SavePendingIncomeRequest(
      BigDecimal amount, String description, LocalDate date, String source, Long propertyId) {
    this(amount, description, date, source, propertyId, null, null);
  }
}
