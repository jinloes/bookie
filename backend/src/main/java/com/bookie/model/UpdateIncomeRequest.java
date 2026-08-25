package com.bookie.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateIncomeRequest(
    @NotNull BigDecimal amount,
    @NotBlank String description,
    @NotNull LocalDate date,
    String source,
    Long propertyId,
    Long payerId,
    Long activityId,
    Long categoryId) {

  public UpdateIncomeRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      String source,
      Long propertyId,
      Long payerId,
      Long activityId) {
    this(amount, description, date, source, propertyId, payerId, activityId, null);
  }

  public UpdateIncomeRequest(
      BigDecimal amount,
      String description,
      LocalDate date,
      String source,
      Long propertyId,
      Long payerId) {
    this(amount, description, date, source, propertyId, payerId, null, null);
  }
}
