package com.bookie.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavePendingIncomeRequest(
    BigDecimal amount, String description, LocalDate date, String source, Long propertyId) {}
