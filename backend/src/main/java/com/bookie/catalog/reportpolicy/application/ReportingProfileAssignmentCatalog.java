package com.bookie.catalog.reportpolicy.application;

import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;

public interface ReportingProfileAssignmentCatalog {

  void synchronizeCurrent(Long activityId, ReportingProfileKey reportingProfileKey);

  void removeAll(Long activityId);
}
