package com.bookie.catalog.reportpolicy.infrastructure;

import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.catalog.reportpolicy.application.ReportingProfilePeriodResolution;
import com.bookie.catalog.reportpolicy.application.ReportingProfileResolution;
import com.bookie.catalog.reportpolicy.domain.ActivityReportingProfileAssignment;
import com.bookie.catalog.reportpolicy.domain.CategoryReportingMapping;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class JpaReportPolicyResolver implements ReportPolicyResolver {

  private final CategoryCatalog categoryCatalog;
  private final ActivityReportingProfileAssignmentRepository assignmentRepository;
  private final CategoryReportingMappingRepository mappingRepository;

  @Override
  @Transactional(readOnly = true)
  public ReportingProfileResolution resolveProfile(Long activityId, LocalDate effectiveOn) {
    List<ActivityReportingProfileAssignment> assignments =
        assignmentRepository.findEffective(activityId, effectiveOn);
    if (assignments.isEmpty()) {
      return new ReportingProfileResolution.Missing(activityId, effectiveOn);
    }
    if (assignments.size() > 1) {
      return new ReportingProfileResolution.Ambiguous(activityId, effectiveOn, assignments.size());
    }
    return new ReportingProfileResolution.Resolved(assignments.getFirst().getReportingProfile());
  }

  @Override
  @Transactional(readOnly = true)
  public ReportingProfilePeriodResolution resolveProfileForPeriod(
      Long activityId, LocalDate effectiveFrom, LocalDate effectiveTo) {
    if (effectiveFrom.isAfter(effectiveTo)) {
      throw new IllegalArgumentException("Reporting profile period start must not follow its end");
    }
    List<ActivityReportingProfileAssignment> assignments =
        assignmentRepository.findOverlapping(activityId, effectiveFrom, effectiveTo);
    if (assignments.isEmpty()) {
      return new ReportingProfilePeriodResolution.Missing(activityId, effectiveFrom, effectiveTo);
    }

    ReportingProfile reportingProfile = null;
    LocalDate nextUncoveredDate = effectiveFrom;
    boolean periodCovered = false;
    for (ActivityReportingProfileAssignment assignment : assignments) {
      if (periodCovered) {
        return new ReportingProfilePeriodResolution.Ambiguous(
            activityId, effectiveFrom, effectiveTo, assignments.size());
      }
      LocalDate assignmentStart =
          assignment.getEffectiveFrom().isBefore(effectiveFrom)
              ? effectiveFrom
              : assignment.getEffectiveFrom();
      LocalDate assignmentEnd =
          assignment.getEffectiveTo() == null || assignment.getEffectiveTo().isAfter(effectiveTo)
              ? effectiveTo
              : assignment.getEffectiveTo();
      if (assignmentStart.isAfter(nextUncoveredDate)) {
        return new ReportingProfilePeriodResolution.Missing(activityId, effectiveFrom, effectiveTo);
      }
      if (assignmentStart.isBefore(nextUncoveredDate)) {
        return new ReportingProfilePeriodResolution.Ambiguous(
            activityId, effectiveFrom, effectiveTo, assignments.size());
      }
      if (reportingProfile == null) {
        reportingProfile = assignment.getReportingProfile();
      } else if (reportingProfile.getKey() != assignment.getReportingProfile().getKey()) {
        return new ReportingProfilePeriodResolution.SpansMultipleProfiles(
            activityId, effectiveFrom, effectiveTo);
      }
      if (assignmentEnd.equals(effectiveTo)) {
        periodCovered = true;
      } else {
        nextUncoveredDate = assignmentEnd.plusDays(1);
      }
    }
    if (!periodCovered) {
      return new ReportingProfilePeriodResolution.Missing(activityId, effectiveFrom, effectiveTo);
    }
    return new ReportingProfilePeriodResolution.Resolved(reportingProfile);
  }

  @Override
  @Transactional(readOnly = true)
  public ReportPolicyResolution resolve(
      Long activityId, Long legacyCategoryId, LocalDate effectiveOn) {
    ReportingProfileResolution profileResolution = resolveProfile(activityId, effectiveOn);
    ReportPolicyResolution unresolvedProfile = unresolvedProfile(profileResolution);
    if (unresolvedProfile != null) {
      return unresolvedProfile;
    }
    var profile = ((ReportingProfileResolution.Resolved) profileResolution).reportingProfile();
    Optional<LegacyCategoryMap> legacyCategoryMap =
        categoryCatalog.findByLegacyCategoryId(legacyCategoryId);
    if (legacyCategoryMap.isEmpty()) {
      return new ReportPolicyResolution.Missing(
          ReportPolicyResolution.Component.LEGACY_CATEGORY_MAP);
    }
    var neutralCategory = legacyCategoryMap.orElseThrow().getNeutralCategory();
    ReportPolicyResolution resolution = resolveMapping(neutralCategory, profile);
    if (resolution instanceof ReportPolicyResolution.Resolved resolved
        && !legacyCategoryId.equals(resolved.legacyCategoryId())) {
      return new ReportPolicyResolution.Incompatible(legacyCategoryId, resolved.legacyCategoryId());
    }
    return resolution;
  }

  @Override
  @Transactional(readOnly = true)
  public ReportPolicyResolution resolveNeutral(
      Long activityId, NeutralCategory neutralCategory, LocalDate effectiveOn) {
    Objects.requireNonNull(neutralCategory, "neutralCategory");
    ReportingProfileResolution profileResolution = resolveProfile(activityId, effectiveOn);
    ReportPolicyResolution unresolvedProfile = unresolvedProfile(profileResolution);
    if (unresolvedProfile != null) {
      return unresolvedProfile;
    }
    var profile = ((ReportingProfileResolution.Resolved) profileResolution).reportingProfile();
    return resolveMapping(neutralCategory, profile);
  }

  private ReportPolicyResolution unresolvedProfile(ReportingProfileResolution profileResolution) {
    if (profileResolution instanceof ReportingProfileResolution.Missing) {
      return new ReportPolicyResolution.Missing(
          ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT);
    }
    if (profileResolution instanceof ReportingProfileResolution.Ambiguous ambiguous) {
      return new ReportPolicyResolution.Ambiguous(
          ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT, ambiguous.candidateCount());
    }
    return null;
  }

  private ReportPolicyResolution resolveMapping(
      NeutralCategory neutralCategory, ReportingProfile profile) {
    List<CategoryReportingMapping> mappings =
        mappingRepository.findAllByNeutralCategoryIdAndReportingProfileId(
            neutralCategory.getId(), profile.getId());
    if (mappings.isEmpty()) {
      return new ReportPolicyResolution.Missing(
          ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING);
    }
    if (mappings.size() > 1) {
      return new ReportPolicyResolution.Ambiguous(
          ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING, mappings.size());
    }
    CategoryReportingMapping mapping = mappings.getFirst();
    return new ReportPolicyResolution.Resolved(
        neutralCategory,
        profile,
        mapping.getLegacyCategoryId(),
        mapping.getReportLine(),
        mapping.isActive());
  }
}
