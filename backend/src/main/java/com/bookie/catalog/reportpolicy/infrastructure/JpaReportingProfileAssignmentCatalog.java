package com.bookie.catalog.reportpolicy.infrastructure;

import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportingProfileAssignmentCatalog;
import com.bookie.catalog.reportpolicy.domain.ActivityReportingProfileAssignment;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaReportingProfileAssignmentCatalog implements ReportingProfileAssignmentCatalog {

  // Matches the V11 seed: an activity's first profile covers all history, so transactions dated
  // before the activity was created (and reports for years that began earlier) still resolve.
  static final LocalDate FIRST_ASSIGNMENT_START = LocalDate.of(1, 1, 1);

  private final ReportingProfileRepository reportingProfileRepository;
  private final ActivityReportingProfileAssignmentRepository assignmentRepository;
  private final Clock clock;

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
      LocalDate effectiveFrom =
          assignmentRepository.existsByActivityId(activityId)
              ? effectiveOn
              : FIRST_ASSIGNMENT_START;
      assignmentRepository.save(newAssignment(activityId, reportingProfile, effectiveFrom));
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
