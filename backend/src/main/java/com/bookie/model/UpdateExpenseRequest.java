package com.bookie.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateExpenseRequest(
    @NotNull BigDecimal amount,
    @NotBlank String description,
    @NotNull LocalDate date,
    @NotNull ExpenseCategory category,
    Long propertyId,
    Long payerId,
    String receiptOneDriveId,
    String receiptFileName) {}
