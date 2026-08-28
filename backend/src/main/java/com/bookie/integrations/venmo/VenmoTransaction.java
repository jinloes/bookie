package com.bookie.integrations.venmo;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record VenmoTransaction(
    String sourceId, String sender, BigDecimal amount, LocalDate date, String description) {}
