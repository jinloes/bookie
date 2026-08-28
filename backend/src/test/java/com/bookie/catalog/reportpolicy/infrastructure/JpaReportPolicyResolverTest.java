package com.bookie.catalog.reportpolicy.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportingProfilePeriodResolution;
import com.bookie.catalog.reportpolicy.application.ReportingProfileResolution;
import com.bookie.catalog.reportpolicy.domain.ActivityReportingProfileAssignment;
import com.bookie.catalog.reportpolicy.domain.CategoryReportingMapping;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import com.bookie.model.TransactionDirection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaReportPolicyResolverTest {

  private static final LocalDate EFFECTIVE_ON = LocalDate.of(2026, 6, 1);

  @Mock private CategoryCatalog categoryCatalog;
  @Mock private ActivityReportingProfileAssignmentRepository assignmentRepository;
  @Mock private CategoryReportingMappingRepository mappingRepository;

  private JpaReportPolicyResolver resolver;

  @BeforeEach
  void setUp() {
    resolver =
        new JpaReportPolicyResolver(categoryCatalog, assignmentRepository, mappingRepository);
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
  class ResolvePolicy {

    @Test
    void propagatesAMissingActivityAssignment() {
      when(assignmentRepository.findEffective(1L, EFFECTIVE_ON)).thenReturn(List.of());

      assertThat(resolver.resolve(1L, 10L, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT));
    }

    @Test
    void propagatesAnAmbiguousActivityAssignment() {
      ReportingProfile profile = profile(1L, ReportingProfileKey.W2);
      when(assignmentRepository.findEffective(1L, EFFECTIVE_ON))
          .thenReturn(
              List.of(
                  assignment(1L, profile, LocalDate.MIN, null),
                  assignment(2L, profile, LocalDate.MIN, null)));

      assertThat(resolver.resolve(1L, 10L, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Ambiguous(
                  ReportPolicyResolution.Component.ACTIVITY_ASSIGNMENT, 2));
    }

    @Test
    void reportsAMissingLegacyCategoryMap() {
      stubProfile(ReportingProfileKey.W2);
      when(categoryCatalog.findByLegacyCategoryId(10L)).thenReturn(Optional.empty());

      assertThat(resolver.resolve(1L, 10L, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.LEGACY_CATEGORY_MAP));
    }

    @Test
    void reportsAMissingCategoryMapping() {
      ReportingProfile profile = stubProfile(ReportingProfileKey.W2);
      NeutralCategory neutral = neutralCategory();
      when(categoryCatalog.findByLegacyCategoryId(10L))
          .thenReturn(Optional.of(legacyMap(10L, neutral)));
      when(mappingRepository.findAllByNeutralCategoryIdAndReportingProfileId(
              neutral.getId(), profile.getId()))
          .thenReturn(List.of());

      assertThat(resolver.resolve(1L, 10L, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING));
    }

    @Test
    void reportsAmbiguousCategoryMappings() {
      ReportingProfile profile = stubProfile(ReportingProfileKey.W2);
      NeutralCategory neutral = neutralCategory();
      when(categoryCatalog.findByLegacyCategoryId(10L))
          .thenReturn(Optional.of(legacyMap(10L, neutral)));
      when(mappingRepository.findAllByNeutralCategoryIdAndReportingProfileId(
              neutral.getId(), profile.getId()))
          .thenReturn(
              List.of(
                  mapping(1L, 10L, neutral, profile, true),
                  mapping(2L, 11L, neutral, profile, true)));

      assertThat(resolver.resolve(1L, 10L, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Ambiguous(
                  ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING, 2));
    }

    @Test
    void reportsAProfileMappingForADifferentLegacyAliasAsIncompatible() {
      ReportingProfile profile = stubProfile(ReportingProfileKey.W2);
      NeutralCategory neutral = neutralCategory();
      when(categoryCatalog.findByLegacyCategoryId(10L))
          .thenReturn(Optional.of(legacyMap(10L, neutral)));
      when(mappingRepository.findAllByNeutralCategoryIdAndReportingProfileId(
              neutral.getId(), profile.getId()))
          .thenReturn(List.of(mapping(1L, 11L, neutral, profile, true)));

      assertThat(resolver.resolve(1L, 10L, EFFECTIVE_ON))
          .isEqualTo(new ReportPolicyResolution.Incompatible(10L, 11L));
    }

    @Test
    void returnsTheExactLegacyMappingIncludingItsActiveState() {
      ReportingProfile profile = stubProfile(ReportingProfileKey.W2);
      NeutralCategory neutral = neutralCategory();
      when(categoryCatalog.findByLegacyCategoryId(10L))
          .thenReturn(Optional.of(legacyMap(10L, neutral)));
      when(mappingRepository.findAllByNeutralCategoryIdAndReportingProfileId(
              neutral.getId(), profile.getId()))
          .thenReturn(List.of(mapping(1L, 10L, neutral, profile, false)));

      assertThat(resolver.resolve(1L, 10L, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Resolved(
                  neutral, profile, 10L, "Schedule 1 educator expense", false));
    }
  }

  @Nested
  class ResolveNeutralPolicy {

    @Test
    void returnsTheEffectiveMappingWithoutALegacyLookup() {
      ReportingProfile profile = stubProfile(ReportingProfileKey.SCHEDULE_E);
      NeutralCategory neutral = neutralCategory();
      CategoryReportingMapping mapping = mapping(1L, 10L, neutral, profile, true);
      when(mappingRepository.findAllByNeutralCategoryIdAndReportingProfileId(
              neutral.getId(), profile.getId()))
          .thenReturn(List.of(mapping));

      assertThat(resolver.resolveNeutral(1L, neutral, EFFECTIVE_ON))
          .isEqualTo(
              new ReportPolicyResolution.Resolved(
                  neutral, profile, 10L, "Schedule 1 educator expense", true));
    }

    @Test
    void propagatesAMissingActivityAssignment() {
      NeutralCategory neutral = neutralCategory();
      when(assignmentRepository.findEffective(1L, EFFECTIVE_ON)).thenReturn(List.of());

      assertThat(resolver.resolveNeutral(1L, neutral, EFFECTIVE_ON))
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

  private LegacyCategoryMap legacyMap(Long legacyCategoryId, NeutralCategory neutralCategory) {
    return LegacyCategoryMap.builder()
        .legacyCategoryId(legacyCategoryId)
        .legacyCategoryKey("EDUCATOR_EXPENSE")
        .neutralCategory(neutralCategory)
        .build();
  }

  private CategoryReportingMapping mapping(
      Long id,
      Long legacyCategoryId,
      NeutralCategory neutralCategory,
      ReportingProfile reportingProfile,
      boolean active) {
    return CategoryReportingMapping.builder()
        .id(id)
        .neutralCategory(neutralCategory)
        .reportingProfile(reportingProfile)
        .legacyCategoryId(legacyCategoryId)
        .reportLine("Schedule 1 educator expense")
        .active(active)
        .build();
  }
}
