package com.bookie.model;

public record OutlookEmail(
    String id, String subject, String sender, String receivedAt, String preview, Long expenseId) {}
