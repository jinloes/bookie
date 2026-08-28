package com.bookie.catalog.reportpolicy.application;

import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import java.time.LocalDate;

public sealed interface ReportingProfilePeriodResolution {

  record Resolved(ReportingProfile reportingProfile) implements ReportingProfilePeriodResolution {}

  record Missing(Long activityId, LocalDate effectiveFrom, LocalDate effectiveTo)
      implements ReportingProfilePeriodResolution {}

  record Ambiguous(
      Long activityId, LocalDate effectiveFrom, LocalDate effectiveTo, int candidateCount)
      implements ReportingProfilePeriodResolution {}

  record SpansMultipleProfiles(Long activityId, LocalDate effectiveFrom, LocalDate effectiveTo)
      implements ReportingProfilePeriodResolution {}
}
