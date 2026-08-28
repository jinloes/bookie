package com.bookie.catalog.activity.api;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.household.api.HouseholdMemberRefResponse;
import com.bookie.catalog.property.api.PropertyRefResponse;

public record FinancialActivityResponse(
    Long id,
    String name,
    ActivityType activityType,
    TaxTreatment taxTreatment,
    HouseholdMemberRefResponse owner,
    PropertyRefResponse property,
    boolean active,
    boolean needsClassification) {

  public static FinancialActivityResponse from(FinancialActivity activity) {
    if (activity == null) {
      return null;
    }
    return new FinancialActivityResponse(
        activity.getId(),
        activity.getName(),
        activity.getActivityType(),
        activity.getTaxTreatment(),
        HouseholdMemberRefResponse.from(activity.getOwner()),
        PropertyRefResponse.from(activity.getProperty()),
        activity.isActive(),
        activity.getSystemKey() != null);
  }
}
