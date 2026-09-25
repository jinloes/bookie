package com.bookie.intake.sources;

import lombok.Builder;

@Builder
public record OutlookEmail(
    String id,
    String subject,
    String sender,
    String receivedAt,
    String preview,
    Long activityId,
    Long expenseId,
    Long inboxItemId,
    String inboxState,
    int pendingCount) {}
