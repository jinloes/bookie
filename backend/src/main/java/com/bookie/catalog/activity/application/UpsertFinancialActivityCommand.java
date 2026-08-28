package com.bookie.catalog.activity.application;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.TaxTreatment;

public record UpsertFinancialActivityCommand(
    String name,
    ActivityType activityType,
    TaxTreatment taxTreatment,
    Long ownerId,
    Long propertyId,
    Boolean active) {}
