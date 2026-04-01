package com.bookie.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavePendingExpenseRequest(
    BigDecimal amount,
    String description,
    LocalDate date,
    String category,
    Long propertyId,
    Long payerId) {}
