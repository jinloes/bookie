package com.bookie.catalog.reportpolicy.application;

import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import java.time.LocalDate;

public sealed interface ReportingProfileResolution {

  record Resolved(ReportingProfile reportingProfile) implements ReportingProfileResolution {}

  record Missing(Long activityId, LocalDate effectiveOn) implements ReportingProfileResolution {}

  record Ambiguous(Long activityId, LocalDate effectiveOn, int candidateCount)
      implements ReportingProfileResolution {}
}
