package com.bookie.intake.application;

/**
 * Published inside the save transaction when a saved inbox item scheduled an external-sync job, so
 * the job is kicked only after that transaction commits.
 */
public record InboxItemSaved(Long inboxItemId) {}
