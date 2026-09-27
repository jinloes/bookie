package com.bookie.intake.application;

/**
 * Published inside the transaction that queued a background job without an inbox item (a manually
 * entered receipt's move, or its retry), so the job is kicked only after that transaction commits.
 */
public record BackgroundJobQueued(Long jobId) {}
