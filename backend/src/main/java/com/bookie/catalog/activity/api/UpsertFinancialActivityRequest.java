package com.bookie.catalog.activity.api;

import com.bookie.catalog.activity.application.UpsertFinancialActivityCommand;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.TaxTreatment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertFinancialActivityRequest(
    @NotBlank String name,
    @NotNull ActivityType activityType,
    @NotNull TaxTreatment taxTreatment,
    @NotNull Long ownerId,
    Long propertyId,
    Boolean active) {

  UpsertFinancialActivityCommand toCommand() {
    return new UpsertFinancialActivityCommand(
        name, activityType, taxTreatment, ownerId, propertyId, active);
  }
}
