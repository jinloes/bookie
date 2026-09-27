package com.bookie.integrations.bank;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

/**
 * One parsed statement transaction. {@code sourceId} is a one-way hash of the bank, account and
 * transaction IDs so the raw account number never leaves the parser.
 */
@Builder
public record BankTransaction(
    String sourceId, BigDecimal amount, LocalDate date, String name, String memo) {}
