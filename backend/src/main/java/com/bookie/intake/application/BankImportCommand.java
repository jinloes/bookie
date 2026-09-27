package com.bookie.intake.application;

import lombok.Builder;

@Builder
public record BankImportCommand(byte[] content, Long activityId) {}
