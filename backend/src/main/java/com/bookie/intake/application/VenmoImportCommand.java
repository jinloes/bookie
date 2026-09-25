package com.bookie.intake.application;

import lombok.Builder;

@Builder
public record VenmoImportCommand(
    byte[] content, String fileName, Long counterpartyId, Long activityId, Long propertyId) {}
