package com.bookie.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateIncomeRequest(
    BigDecimal amount,
    String description,
    LocalDate date,
    String source,
    Long propertyId,
    ExpenseSource sourceType,
    String receiptOneDriveId,
    String receiptFileName) {}
