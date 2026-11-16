package com.bookie.catalog.reportpolicy.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportingProfilePeriodResolution;
import com.bookie.catalog.reportpolicy.application.ReportingProfileResolution;
import com.bookie.catalog.reportpolicy.domain.ActivityReportingProfileAssignment;
import com.bookie.catalog.reportpolicy.domain.CategoryReportingMapping;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaReportPolicyResolverTest {

  private static final LocalDate EFFECTIVE_ON = LocalDate.of(2026, 6, 1);

  @Mock private ActivityReportingProfileAssignmentRepository assignmentRepository;
  @Mock private CategoryReportingMappingRepository mappingRepository;

  private JpaReportPolicyResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new JpaReportPolicyResolver(assignmentRepository, mappingRepository);
  }

  @Nested
  class ResolveProfile {

    @Test
    void returnsMissingWhenNoAssignmentIsEffective() {
      when(assignmentRepository.findEffective(1L, EFFECTIVE_ON)).thenReturn(List.of());

      assertThat(resolver.resolveProfile(1L, EFFECTIVE_ON))
          .isEqualTo(new ReportingProfileResolution.Missing(1L, EFFECTIVE_ON));
    }

    @Test
    void returnsAmbiguousWhenMultipleAssignmentsAreEffective() {
      when(assignmentRepository.findEffective(1L, EFFECTIVE_ON))
          .thenReturn(
              List.of(
                  assignment(1L, profile(1L, ReportingProfileKey.W2), EFFECTIVE_ON, null),
                  assignment(2L, profile(2L, ReportingProfileKey.SCHEDULE_C), EFFECTIVE_ON, null)));

      assertThat(resolver.resolveProfile(1L, EFFECTIVE_ON))
          .isEqualTo(new ReportingProfileResolution.Ambiguous(1L, EFFECTIVE_ON, 2));
    }

    @Test
    void returnsTheSingleEffectiveProfile() {
      ReportingProfile profile = profile(1L, ReportingProfileKey.W2);
      when(assignmentRepository.findEffective(1L, EFFECTIVE_ON))
          .thenReturn(List.of(assignment(1L, profile, LocalDate.MIN, null)));

      assertThat(resolver.resolveProfile(1L, EFFECTIVE_ON))
          .isEqualTo(new ReportingProfileResolution.Resolved(profile));
    }
  }

  @Nested
  class ResolveProfileForPeriod {

    private final LocalDate from = LocalDate.of(2026, 1, 1);
    private final LocalDate to = LocalDate.of(2026, 12, 31);

    @Test
    void rejectsAnInvertedPeriod() {
      assertThatThrownBy(() -> resolver.resolveProfileForPeriod(1L, to, from))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("start");
    }

    @Test
    void returnsMissingWhenNoAssignmentOverlaps() {
      when(assignmentRepository.findOverlapping(1L, from, to)).thenReturn(List.of());

      assertThat(resolver.resolveProfileForPeriod(1L, from, to))
          .isEqualTo(new ReportingProfilePeriodResolution.Missing(1L, from, to));
    }

    @Test
    void resolvesOneAssignmentCoveringTheWholePeriod() {
      ReportingProfile profile = profile(1L, ReportingProfileKey.SCHEDULE_E);
      when(assignmentRepository.findOverlapping(1L, from, to))
          .thenReturn(List.of(assignment(1L, profile, LocalDate.MIN, null)));

      assertThat(resolver.resolveProfileForPeriod(1L, from, to))
          .isEqualTo(new ReportingProfilePeriodResolution.Resolved(profile));
    }

    @Test
    void resolvesContiguousAssignmentsForTheSameProfile() {
      ReportingProfile profile = profile(1L, ReportingProfileKey.SCHEDULE_E);
      when(assignmentRepository.findOverlapping(1L, from, to))
          .thenReturn(
              List.of(
                  assignment(1L, profile, LocalDate.MIN, LocalDate.of(2026, 5, 31)),
                  assignment(2L, profile, LocalDate.of(2026, 6, 1), null)));

      assertThat(resolver.resolveProfileForPeriod(1L, from, to))
          .isEqualTo(new ReportingProfilePeriodResolution.Resolved(profile));
    }

    @Test
    void returnsMissingWhenAssignmentsLeaveAGap() {
      ReportingProfile profile = profile(1L, ReportingProfileKey.SCHEDULE_E);
      when(assignmentRepository.findOverlapping(1L, from, to))
          .thenReturn(
              List.of(
                  assignment(1L, profile, LocalDate.MIN, LocalDate.of(2026, 5, 31)),
                  assignment(2L, profile, LocalDate.of(2026, 6, 2), null)));

      assertThat(resolver.resolveProfileForPeriod(1L, from, to))
          .isEqualTo(new ReportingProfilePeriodResolution.Missing(1L, from, to));
    }

    @Test
    void returnsAmbiguousWhenAssignmentsOverlap() {
      ReportingProfile profile = profile(1L, ReportingProfileKey.SCHEDULE_E);
      when(assignmentRepository.findOverlapping(1L, from, to))
          .thenReturn(
              List.of(
                  assignment(1L, profile, LocalDate.MIN, LocalDate.of(2026, 6, 1)),
                  assignment(2L, profile, LocalDate.of(2026, 6, 1), null)));

      assertThat(resolver.resolveProfileForPeriod(1L, from, to))
          .isEqualTo(new ReportingProfilePeriodResolution.Ambiguous(1L, from, to, 2));
    }

    @Test
    void reportsAProfileChangeInsteadOfTreatingThePeriodAsOnePolicy() {
      when(assignmentRepository.findOverlapping(1L, from, to))
          .thenReturn(
              List.of(
                  assignment(
                      1L,
                      profile(1L, ReportingProfileKey.SCHEDULE_E),
                      LocalDate.MIN,
                      LocalDate.of(2026, 5, 31)),
                  assignment(
                      2L,
                      profile(2L, ReportingProfileKey.SCHEDULE_C),
                      LocalDate.of(2026, 6, 1),
                      null)));

      assertThat(resolver.resolveProfileForPeriod(1L, from, to))
          .isEqualTo(new ReportingProfilePeriodResolution.SpansMultipleProfiles(1L, from, to));
    }
  }

  @Nested
  class ResolveNeutralPolicy {

    @Test
    void propagatesAMissingActivityAssignment() {
      NeutralCategory neutral = neutralCategory();
      when(assignmentRepository.findEffective(1L, EFFECTIVE_ON)).thenReturn(List.of());

      assertThat(resolver.resolveNeutral(1L, neutral, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT));
    }

    @Test
    void propagatesAnAmbiguousActivityAssignment() {
      NeutralCategory neutral = neutralCategory();
      ReportingProfile profile = profile(1L, ReportingProfileKey.W2);
      when(assignmentRepository.findEffective(1L, EFFECTIVE_ON))
          .thenReturn(
              List.of(
                  assignment(1L, profile, LocalDate.MIN, null),
                  assignment(2L, profile, LocalDate.MIN, null)));

      assertThat(resolver.resolveNeutral(1L, neutral, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Ambiguous(
                  ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT, 2));
    }

    @Test
    void reportsAMissingCategoryMappingWhenUnmappedForProfile() {
      ReportingProfile profile = stubProfile(ReportingProfileKey.W2);
      NeutralCategory neutral = neutralCategory();
      when(mappingRepository.findAllByNeutralCategoryIdAndReportingProfileId(
              neutral.getId(), profile.getId()))
          .thenReturn(List.of());

      assertThat(resolver.resolveNeutral(1L, neutral, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING));
    }

    @Test
    void reportsAmbiguousCategoryMappings() {
      ReportingProfile profile = stubProfile(ReportingProfileKey.W2);
      NeutralCategory neutral = neutralCategory();
      when(mappingRepository.findAllByNeutralCategoryIdAndReportingProfileId(
              neutral.getId(), profile.getId()))
          .thenReturn(
              List.of(mapping(1L, neutral, profile, true), mapping(2L, neutral, profile, true)));

      assertThat(resolver.resolveNeutral(1L, neutral, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Ambiguous(
                  ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING, 2));
    }

    @Test
    void returnsTheExactMappingIncludingItsActiveState() {
      ReportingProfile profile = stubProfile(ReportingProfileKey.W2);
      NeutralCategory neutral = neutralCategory();
      when(mappingRepository.findAllByNeutralCategoryIdAndReportingProfileId(
              neutral.getId(), profile.getId()))
          .thenReturn(List.of(mapping(1L, neutral, profile, false)));

      assertThat(resolver.resolveNeutral(1L, neutral, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Resolved(
                  neutral, profile, "Schedule 1 educator expense", false));
    }
  }

  @Nested
  class BatchPeriodPolicy {

    private final LocalDate from = LocalDate.of(2026, 1, 1);
    private final LocalDate to = LocalDate.of(2026, 12, 31);

    @Test
    void preloadsAssignmentsAndMappingsOnceAcrossTransactions() {
      ReportingProfile profile = profile(1L, ReportingProfileKey.SCHEDULE_E);
      NeutralCategory neutral = neutralCategory();
      CategoryReportingMapping mapping = mapping(1L, neutral, profile, true);
      when(assignmentRepository.findAllOverlapping(java.util.Set.of(1L), from, to))
          .thenReturn(List.of(assignment(1L, profile, LocalDate.MIN, null)));
      when(mappingRepository.findAllByReportingProfileIdIn(java.util.Set.of(1L)))
          .thenReturn(List.of(mapping));

      var batch = resolver.forPeriod(java.util.Set.of(1L), from, to);

      assertThat(batch.resolveNeutral(1L, neutral, LocalDate.of(2026, 3, 15)))
          .isEqualTo(
              new ReportPolicyResolution.Resolved(
                  neutral, profile, "Schedule 1 educator expense", true));
      assertThat(batch.resolveNeutral(1L, neutral, LocalDate.of(2026, 9, 15)))
          .isEqualTo(
              new ReportPolicyResolution.Resolved(
                  neutral, profile, "Schedule 1 educator expense", true));
      org.mockito.Mockito.verify(assignmentRepository, org.mockito.Mockito.times(1))
          .findAllOverlapping(java.util.Set.of(1L), from, to);
      org.mockito.Mockito.verify(mappingRepository, org.mockito.Mockito.times(1))
          .findAllByReportingProfileIdIn(java.util.Set.of(1L));
    }

    @Test
    void handlesEmptyActivitiesMissingAndAmbiguousAssignments() {
      NeutralCategory neutral = neutralCategory();
      assertThatThrownBy(() -> resolver.forPeriod(java.util.Set.of(1L), to, from))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(resolver.forPeriod(java.util.Set.of(), from, to).resolveNeutral(1L, neutral, from))
          .isEqualTo(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT));

      ReportingProfile profile = profile(1L, ReportingProfileKey.SCHEDULE_E);
      when(assignmentRepository.findAllOverlapping(java.util.Set.of(1L, 2L), from, to))
          .thenReturn(
              List.of(
                  assignment(1L, profile, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)),
                  assignment(2L, profile, LocalDate.of(2026, 6, 1), null)));
      when(mappingRepository.findAllByReportingProfileIdIn(java.util.Set.of(1L)))
          .thenReturn(List.of());

      var batch = resolver.forPeriod(java.util.Set.of(1L, 2L), from, to);
      assertThat(batch.resolveNeutral(1L, neutral, LocalDate.of(2026, 3, 1)))
          .isEqualTo(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT));
      assertThat(batch.resolveNeutral(1L, neutral, LocalDate.of(2026, 7, 1)))
          .isEqualTo(
              new ReportPolicyResolution.Ambiguous(
                  ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT, 2));
      assertThat(batch.resolveNeutral(2L, neutral, LocalDate.of(2026, 7, 1)))
          .isEqualTo(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT));
    }
  }

  private ReportingProfile stubProfile(ReportingProfileKey key) {
    ReportingProfile profile = profile(1L, key);
    when(assignmentRepository.findEffective(1L, EFFECTIVE_ON))
        .thenReturn(List.of(assignment(1L, profile, LocalDate.MIN, null)));
    return profile;
  }

  private ReportingProfile profile(Long id, ReportingProfileKey key) {
    return ReportingProfile.builder().id(id).key(key).label(key.name()).active(true).build();
  }

  private ActivityReportingProfileAssignment assignment(
      Long id, ReportingProfile profile, LocalDate effectiveFrom, LocalDate effectiveTo) {
    return ActivityReportingProfileAssignment.builder()
        .id(id)
        .activityId(1L)
        .reportingProfile(profile)
        .effectiveFrom(effectiveFrom)
        .effectiveTo(effectiveTo)
        .build();
  }

  private NeutralCategory neutralCategory() {
    return NeutralCategory.builder()
        .id(20L)
        .key("EDUCATOR_EXPENSE")
        .label("Educator expense")
        .direction(TransactionDirection.EXPENSE)
        .active(true)
        .system(true)
        .build();
  }

  private CategoryReportingMapping mapping(
      Long id, NeutralCategory neutralCategory, ReportingProfile reportingProfile, boolean active) {
    return CategoryReportingMapping.builder()
        .id(id)
        .neutralCategory(neutralCategory)
        .reportingProfile(reportingProfile)
        .reportLine("Schedule 1 educator expense")
        .active(active)
        .build();
  }
}
