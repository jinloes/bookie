package com.bookie.intake.api;

import com.bookie.intake.domain.BackgroundJob;
import com.bookie.intake.domain.BackgroundJobState;
import com.bookie.intake.domain.BackgroundJobType;
import java.time.LocalDateTime;

public record BackgroundJobResponse(
    Long id,
    Long inboxItemId,
    BackgroundJobType type,
    BackgroundJobState state,
    int attempts,
    int maxAttempts,
    LocalDateTime availableAt,
    LocalDateTime leaseExpiresAt,
    String lastError,
    String terminalReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long version) {

  public static BackgroundJobResponse from(BackgroundJob job) {
    return new BackgroundJobResponse(
        job.getId(),
        job.getInboxItem().getId(),
        job.getType(),
        job.getState(),
        job.getAttempts(),
        job.getMaxAttempts(),
        job.getAvailableAt(),
        job.getLeaseExpiresAt(),
        job.getLastError(),
        job.getTerminalReason(),
        job.getCreatedAt(),
        job.getUpdatedAt(),
        job.getVersion());
  }
}
