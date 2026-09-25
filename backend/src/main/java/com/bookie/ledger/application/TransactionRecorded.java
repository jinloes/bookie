package com.bookie.ledger.application;

import java.time.LocalDate;

/**
 * Published inside the create transaction when a manually entered RECEIPT transaction carries an
 * attachment, so its receipt is filed into the tax-year folder only after the transaction commits.
 */
public record TransactionRecorded(
    Long transactionId, String origin, String attachmentExternalId, LocalDate date) {}
