package com.bookie.catalog.reportpolicy.application;

import com.bookie.catalog.category.domain.NeutralCategory;
import java.time.LocalDate;
import java.util.Set;

public interface ReportPolicyResolver {

  ReportingProfileResolution resolveProfile(Long activityId, LocalDate effectiveOn);

  ReportingProfilePeriodResolution resolveProfileForPeriod(
      Long activityId, LocalDate effectiveFrom, LocalDate effectiveTo);

  ReportPolicyResolution resolveNeutral(
      Long activityId, NeutralCategory neutralCategory, LocalDate effectiveOn);

  default ReportPolicyBatchResolver forPeriod(
      Set<Long> activityIds, LocalDate effectiveFrom, LocalDate effectiveTo) {
    return this::resolveNeutral;
  }

  @FunctionalInterface
  interface ReportPolicyBatchResolver {
    ReportPolicyResolution resolveNeutral(
        Long activityId, NeutralCategory neutralCategory, LocalDate effectiveOn);
  }
}
