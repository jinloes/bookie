package com.bookie.model;

import lombok.Builder;

@Builder
public record OutlookEmail(
    String id,
    String subject,
    String sender,
    String receivedAt,
    String preview,
    Long expenseId,
    Long pendingId,
    String pendingStatus) {}
