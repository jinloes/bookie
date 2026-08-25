package com.bookie.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SavePendingExpenseRequest(
    @NotNull BigDecimal amount,
    @NotBlank String description,
    @NotNull LocalDate date,
    String category,
    Long propertyId,
    Long payerId,
    Long activityId,
    Long categoryId) {

  public SavePendingExpenseRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      String category,
      Long propertyId,
      Long payerId,
      Long activityId) {
    this(amount, description, date, category, propertyId, payerId, activityId, null);
  }

  public SavePendingExpenseRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      String category,
      Long propertyId,
      Long payerId) {
    this(amount, description, date, category, propertyId, payerId, null, null);
  }
}
