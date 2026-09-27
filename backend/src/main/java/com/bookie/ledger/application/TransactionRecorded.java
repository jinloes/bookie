package com.bookie.ledger.application;

import java.time.LocalDate;

/**
 * Published inside the create transaction when a manually entered RECEIPT transaction carries an
 * attachment. Listeners run in that transaction, so work they schedule commits or rolls back with
 * the transaction.
 */
public record TransactionRecorded(
    Long transactionId, String origin, String attachmentExternalId, LocalDate date) {}
