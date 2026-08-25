package com.bookie.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertFinancialActivityRequest(
    @NotBlank String name,
    @NotNull ActivityType activityType,
    @NotNull TaxTreatment taxTreatment,
    @NotNull Long ownerId,
    Long propertyId,
    Boolean active) {}
