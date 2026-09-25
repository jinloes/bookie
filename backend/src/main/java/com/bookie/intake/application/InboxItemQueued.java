package com.bookie.intake.application;

/**
 * Published inside the transaction that queued a background job for an inbox item (create, requeue,
 * reparse or retry), so the job is kicked only after that transaction commits.
 */
public record InboxItemQueued(Long inboxItemId) {}
