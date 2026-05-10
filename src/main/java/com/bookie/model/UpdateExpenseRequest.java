package com.bookie.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateExpenseRequest(
    BigDecimal amount,
    String description,
    LocalDate date,
    ExpenseCategory category,
    Long propertyId,
    Long payerId,
    String receiptOneDriveId,
    String receiptFileName) {}
