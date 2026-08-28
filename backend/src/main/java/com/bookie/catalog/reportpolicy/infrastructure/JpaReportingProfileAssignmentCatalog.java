package com.bookie.catalog.reportpolicy.infrastructure;

import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportingProfileAssignmentCatalog;
import com.bookie.catalog.reportpolicy.domain.ActivityReportingProfileAssignment;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaReportingProfileAssignmentCatalog implements ReportingProfileAssignmentCatalog {

  private final ReportingProfileRepository reportingProfileRepository;
  private final ActivityReportingProfileAssignmentRepository assignmentRepository;
  private final Clock clock;

  @Autowired
  JpaReportingProfileAssignmentCatalog(
      ReportingProfileRepository reportingProfileRepository,
      ActivityReportingProfileAssignmentRepository assignmentRepository) {
    this(reportingProfileRepository, assignmentRepository, Clock.systemDefaultZone());
  }

  JpaReportingProfileAssignmentCatalog(
      ReportingProfileRepository reportingProfileRepository,
      ActivityReportingProfileAssignmentRepository assignmentRepository,
      Clock clock) {
    this.reportingProfileRepository = reportingProfileRepository;
    this.assignmentRepository = assignmentRepository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void synchronizeCurrent(Long activityId, ReportingProfileKey reportingProfileKey) {
    ReportingProfile reportingProfile =
        reportingProfileRepository
            .findByKeyAndActiveTrue(reportingProfileKey)
            .orElseThrow(
                () ->
                    new ReportPolicyParityException(
                        "Reporting profile is missing: " + reportingProfileKey));
    List<ActivityReportingProfileAssignment> openAssignments =
        assignmentRepository.findAllByActivityIdAndEffectiveToIsNull(activityId);
    if (openAssignments.size() > 1) {
      throw new ReportPolicyParityException(
          "Activity has multiple open reporting profile assignments: " + activityId);
    }

    LocalDate effectiveOn = LocalDate.now(clock);
    if (openAssignments.isEmpty()) {
      assignmentRepository.save(newAssignment(activityId, reportingProfile, effectiveOn));
      return;
    }

    ActivityReportingProfileAssignment current = openAssignments.getFirst();
    if (current.getEffectiveFrom().isAfter(effectiveOn)) {
      throw new ReportPolicyParityException(
          "Activity has a future open reporting profile assignment: " + activityId);
    }
    if (current.getReportingProfile().getKey() == reportingProfileKey) {
      return;
    }
    if (current.getEffectiveFrom().equals(effectiveOn)) {
      current.setReportingProfile(reportingProfile);
      assignmentRepository.save(current);
      return;
    }

    current.setEffectiveTo(effectiveOn.minusDays(1));
    assignmentRepository.saveAndFlush(current);
    assignmentRepository.save(newAssignment(activityId, reportingProfile, effectiveOn));
  }

  @Override
  @Transactional
  public void removeAll(Long activityId) {
    assignmentRepository.deleteAllByActivityId(activityId);
  }

  private ActivityReportingProfileAssignment newAssignment(
      Long activityId, ReportingProfile reportingProfile, LocalDate effectiveFrom) {
    return ActivityReportingProfileAssignment.builder()
        .activityId(activityId)
        .reportingProfile(reportingProfile)
        .effectiveFrom(effectiveFrom)
        .build();
  }
}
