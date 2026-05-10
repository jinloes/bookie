package com.bookie.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExpenseRequest(
    BigDecimal amount,
    String description,
    LocalDate date,
    ExpenseCategory category,
    Long propertyId,
    Long payerId,
    String receiptOneDriveId,
    String receiptFileName,
    ExpenseSource sourceType) {}
