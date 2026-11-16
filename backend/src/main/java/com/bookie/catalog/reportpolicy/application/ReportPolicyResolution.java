package com.bookie.catalog.reportpolicy.application;

import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;

public sealed interface ReportPolicyResolution {

  enum Component {
    ACTIVITY_ASSIGNMENT,
    CATEGORY_REPORTING_MAPPING
  }

  record Resolved(
      NeutralCategory neutralCategory,
      ReportingProfile reportingProfile,
      String reportLine,
      boolean mappingActive)
      implements ReportPolicyResolution {}

  record Missing(Component component) implements ReportPolicyResolution {}

  record Ambiguous(Component component, int candidateCount) implements ReportPolicyResolution {}
}
