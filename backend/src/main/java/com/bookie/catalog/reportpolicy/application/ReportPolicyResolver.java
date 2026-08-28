package com.bookie.catalog.reportpolicy.application;

import com.bookie.catalog.category.domain.NeutralCategory;
import java.time.LocalDate;

public interface ReportPolicyResolver {

  ReportingProfileResolution resolveProfile(Long activityId, LocalDate effectiveOn);

  ReportingProfilePeriodResolution resolveProfileForPeriod(
      Long activityId, LocalDate effectiveFrom, LocalDate effectiveTo);

  ReportPolicyResolution resolve(Long activityId, Long legacyCategoryId, LocalDate effectiveOn);

  ReportPolicyResolution resolveNeutral(
      Long activityId, NeutralCategory neutralCategory, LocalDate effectiveOn);
}
