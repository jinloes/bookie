package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportPolicyReadMode;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.catalog.reportpolicy.application.ReportingProfilePeriodResolution;
import com.bookie.catalog.reportpolicy.application.ReportingProfileResolution;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import com.bookie.model.FinancialCategory;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.FinancialCategoryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FinancialCategoryServiceTest {

  private static final LocalDate EFFECTIVE_ON = LocalDate.of(2026, 6, 15);
  private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
  private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

  @Mock private FinancialCategoryRepository financialCategoryRepository;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private ReportPolicyResolver reportPolicyResolver;

  private FinancialCategoryService service;
  private FinancialActivity teaching;

  @BeforeEach
  void setUp() {
    service =
        new FinancialCategoryService(
            financialCategoryRepository, activityCatalog, reportPolicyResolver);
    teaching =
        FinancialActivity.builder()
            .id(10L)
            .name("Teaching")
            .activityType(ActivityType.EMPLOYMENT)
            .taxTreatment(TaxTreatment.W2)
            .active(true)
            .build();
  }

  @Nested
  class LegacyMode {

    @BeforeEach
    void useLegacyMode() {
      setMode(ReportPolicyReadMode.LEGACY);
    }

    @Test
    void findsCategoriesByActivityTreatmentAndDirection() {
      FinancialCategory wages =
          category(1L, "WAGES", TransactionDirection.INCOME, TaxTreatment.W2, null, true);
      FinancialCategory rentalIncome =
          category(
              2L,
              "RENTAL_INCOME",
              TransactionDirection.INCOME,
              TaxTreatment.SCHEDULE_E,
              "Rents received",
              true);
      when(activityCatalog.findActiveById(10L)).thenReturn(teaching);
      when(financialCategoryRepository.findAllByActiveTrueOrderByDirectionAscLabelAsc())
          .thenReturn(List.of(rentalIncome, wages));

      assertThat(service.findCompatible(TransactionDirection.INCOME, 10L)).containsExactly(wages);
    }

    @Test
    void returnsTheActiveCatalogWithoutAnActivityOrDirectionFilter() {
      FinancialCategory wages =
          category(1L, "WAGES", TransactionDirection.INCOME, TaxTreatment.W2, null, true);
      FinancialCategory educatorExpense =
          category(
              2L,
              "EDUCATOR_EXPENSE",
              TransactionDirection.EXPENSE,
              TaxTreatment.W2,
              "Schedule 1 educator expense",
              true);
      when(financialCategoryRepository.findAllByActiveTrueOrderByDirectionAscLabelAsc())
          .thenReturn(List.of(educatorExpense, wages));

      assertThat(service.findCompatible(null, null)).containsExactly(educatorExpense, wages);
    }

    @Test
    void rejectsACategoryFromADifferentLegacyTaxTreatment() {
      FinancialCategory rental =
          category(
              2L,
              "RENTAL_INCOME",
              TransactionDirection.INCOME,
              TaxTreatment.SCHEDULE_E,
              "Rents received",
              true);
      when(financialCategoryRepository.findById(2L)).thenReturn(Optional.of(rental));

      assertThatThrownBy(
              () -> service.resolve(2L, null, TransactionDirection.INCOME, teaching, EFFECTIVE_ON))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("tax treatment");
    }

    @Test
    void mapsAW2ExpenseToTheLegacyFallback() {
      FinancialCategory employment =
          category(
              3L,
              "EMPLOYMENT_OTHER_EXPENSE",
              TransactionDirection.EXPENSE,
              TaxTreatment.W2,
              null,
              true);
      when(financialCategoryRepository.findByKey("EMPLOYMENT_OTHER_EXPENSE"))
          .thenReturn(Optional.of(employment));

      assertThat(service.defaultFor(teaching, TransactionDirection.EXPENSE, EFFECTIVE_ON))
          .isEqualTo(employment);
    }

    @Test
    void treatsANullCategoryAsIncompatibleForFallbackSelection() {
      assertThat(service.isCompatible(null, teaching, TransactionDirection.EXPENSE, EFFECTIVE_ON))
          .isFalse();
    }

    @Test
    void preservesTheLegacyReportCategoryFilter() {
      FinancialCategory wages =
          category(1L, "WAGES", TransactionDirection.INCOME, TaxTreatment.W2, null, true);
      FinancialCategory rentalIncome =
          category(
              2L,
              "RENTAL_INCOME",
              TransactionDirection.INCOME,
              TaxTreatment.SCHEDULE_E,
              "Rents received",
              true);

      assertThat(
              service.isIncludedInReport(
                  wages, teaching, TaxTreatment.SCHEDULE_E, PERIOD_START, PERIOD_END))
          .isFalse();
      assertThat(
              service.isIncludedInReport(
                  rentalIncome, teaching, TaxTreatment.SCHEDULE_E, PERIOD_START, PERIOD_END))
          .isTrue();
    }
  }

  @Nested
  class CompareMode {

    @BeforeEach
    void useCompareMode() {
      setMode(ReportPolicyReadMode.COMPARE);
    }

    @Test
    void returnsLegacyCompatibleCategoriesAfterExactPolicyParity() {
      FinancialCategory wages =
          category(1L, "WAGES", TransactionDirection.INCOME, TaxTreatment.W2, null, true);
      FinancialCategory rentalIncome =
          category(
              2L,
              "RENTAL_INCOME",
              TransactionDirection.INCOME,
              TaxTreatment.SCHEDULE_E,
              "Rents received",
              true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      LocalDate today = LocalDate.now();
      when(activityCatalog.findActiveById(10L)).thenReturn(teaching);
      when(financialCategoryRepository.findAllByActiveTrueOrderByDirectionAscLabelAsc())
          .thenReturn(List.of(rentalIncome, wages));
      when(reportPolicyResolver.resolveProfile(10L, today))
          .thenReturn(new ReportingProfileResolution.Resolved(w2));
      when(reportPolicyResolver.resolve(10L, 1L, today)).thenReturn(resolved(wages, w2));
      when(reportPolicyResolver.resolve(10L, 2L, today))
          .thenReturn(new ReportPolicyResolution.Incompatible(2L, 1L));

      assertThat(service.findCompatible(TransactionDirection.INCOME, 10L)).containsExactly(wages);
    }

    @Test
    void failsClosedWhenPolicyCompatibleResultsDifferFromLegacy() {
      FinancialCategory wages =
          category(1L, "WAGES", TransactionDirection.INCOME, TaxTreatment.W2, null, true);
      FinancialCategory rentalIncome =
          category(
              2L,
              "RENTAL_INCOME",
              TransactionDirection.INCOME,
              TaxTreatment.SCHEDULE_E,
              "Rents received",
              true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      LocalDate today = LocalDate.now();
      when(activityCatalog.findActiveById(10L)).thenReturn(teaching);
      when(financialCategoryRepository.findAllByActiveTrueOrderByDirectionAscLabelAsc())
          .thenReturn(List.of(rentalIncome, wages));
      when(reportPolicyResolver.resolveProfile(10L, today))
          .thenReturn(new ReportingProfileResolution.Resolved(w2));
      when(reportPolicyResolver.resolve(10L, 1L, today)).thenReturn(resolved(wages, w2));
      when(reportPolicyResolver.resolve(10L, 2L, today)).thenReturn(resolved(rentalIncome, w2));

      assertThatThrownBy(() -> service.findCompatible(TransactionDirection.INCOME, 10L))
          .isInstanceOf(ReportPolicyParityException.class)
          .hasMessageContaining("results differ");
    }

    @Test
    void resolvesTheLegacyCategoryAfterCheckingEveryMappedField() {
      FinancialCategory educatorExpense =
          category(
              3L,
              "EDUCATOR_EXPENSE",
              TransactionDirection.EXPENSE,
              TaxTreatment.W2,
              "Schedule 1 educator expense",
              true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(financialCategoryRepository.findById(3L)).thenReturn(Optional.of(educatorExpense));
      when(reportPolicyResolver.resolve(10L, 3L, EFFECTIVE_ON))
          .thenReturn(resolved(educatorExpense, w2));

      assertThat(service.resolve(3L, null, TransactionDirection.EXPENSE, teaching, EFFECTIVE_ON))
          .isSameAs(educatorExpense);
    }

    @Test
    void failsClosedWhenTheMappedReportLineDiffers() {
      FinancialCategory educatorExpense =
          category(
              3L,
              "EDUCATOR_EXPENSE",
              TransactionDirection.EXPENSE,
              TaxTreatment.W2,
              "Schedule 1 educator expense",
              true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(financialCategoryRepository.findById(3L)).thenReturn(Optional.of(educatorExpense));
      when(reportPolicyResolver.resolve(10L, 3L, EFFECTIVE_ON))
          .thenReturn(
              new ReportPolicyResolution.Resolved(
                  neutral(educatorExpense, true),
                  w2,
                  educatorExpense.getId(),
                  "Different line",
                  true));

      assertThatThrownBy(
              () -> service.resolve(3L, null, TransactionDirection.EXPENSE, teaching, EFFECTIVE_ON))
          .isInstanceOf(ReportPolicyParityException.class)
          .hasMessageContaining("differs");
    }

    @Test
    void usesAnExactProfileAcrossTheWholeReportPeriod() {
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(reportPolicyResolver.resolveProfileForPeriod(10L, PERIOD_START, PERIOD_END))
          .thenReturn(new ReportingProfilePeriodResolution.Resolved(w2));

      assertThat(service.usesReportingProfile(teaching, TaxTreatment.W2, PERIOD_START, PERIOD_END))
          .isTrue();
    }

    @Test
    void failsClosedWhenAReportPeriodSpansMultipleProfiles() {
      when(reportPolicyResolver.resolveProfileForPeriod(10L, PERIOD_START, PERIOD_END))
          .thenReturn(
              new ReportingProfilePeriodResolution.SpansMultipleProfiles(
                  10L, PERIOD_START, PERIOD_END));

      assertThatThrownBy(
              () ->
                  service.usesReportingProfile(teaching, TaxTreatment.W2, PERIOD_START, PERIOD_END))
          .isInstanceOf(ReportPolicyParityException.class)
          .hasMessageContaining("period");
    }

    @Test
    void validatesBothEndsOfAnIncludedReportCategory() {
      FinancialCategory educatorExpense =
          category(
              3L,
              "EDUCATOR_EXPENSE",
              TransactionDirection.EXPENSE,
              TaxTreatment.W2,
              "Schedule 1 educator expense",
              true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(reportPolicyResolver.resolve(10L, 3L, PERIOD_START))
          .thenReturn(resolved(educatorExpense, w2));
      when(reportPolicyResolver.resolve(10L, 3L, PERIOD_END))
          .thenReturn(resolved(educatorExpense, w2));

      assertThat(
              service.isIncludedInReport(
                  educatorExpense, teaching, TaxTreatment.W2, PERIOD_START, PERIOD_END))
          .isTrue();
    }

    @Test
    void excludesAReportCategoryWhenBothModelsExcludeIt() {
      FinancialCategory rentalExpense =
          category(
              4L, "ADVERTISING", TransactionDirection.EXPENSE, TaxTreatment.SCHEDULE_E, "5", true);
      when(reportPolicyResolver.resolve(10L, 4L, PERIOD_START))
          .thenReturn(new ReportPolicyResolution.Incompatible(4L, 40L));
      when(reportPolicyResolver.resolve(10L, 4L, PERIOD_END))
          .thenReturn(new ReportPolicyResolution.Incompatible(4L, 40L));

      assertThat(
              service.isIncludedInReport(
                  rentalExpense, teaching, TaxTreatment.W2, PERIOD_START, PERIOD_END))
          .isFalse();
    }

    @Test
    void failsClosedWhenReportCategoryInclusionDiffers() {
      FinancialCategory rentalExpense =
          category(
              4L, "ADVERTISING", TransactionDirection.EXPENSE, TaxTreatment.SCHEDULE_E, "5", true);
      ReportingProfile scheduleE = profile(ReportingProfileKey.SCHEDULE_E, true);
      when(reportPolicyResolver.resolve(10L, 4L, PERIOD_START))
          .thenReturn(resolved(rentalExpense, scheduleE));
      when(reportPolicyResolver.resolve(10L, 4L, PERIOD_END))
          .thenReturn(resolved(rentalExpense, scheduleE));

      assertThatThrownBy(
              () ->
                  service.isIncludedInReport(
                      rentalExpense, teaching, TaxTreatment.W2, PERIOD_START, PERIOD_END))
          .isInstanceOf(ReportPolicyParityException.class)
          .hasMessageContaining("inclusion");
    }
  }

  @Nested
  class DefaultNewMode {

    @Test
    void acceptsPolicyPlacementIndependentOfTheLegacyTaxTreatmentColumn() {
      FinancialCategory legacyRentalCategory =
          category(
              4L, "ADVERTISING", TransactionDirection.EXPENSE, TaxTreatment.SCHEDULE_E, "5", true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(financialCategoryRepository.findById(4L)).thenReturn(Optional.of(legacyRentalCategory));
      when(reportPolicyResolver.resolve(10L, 4L, EFFECTIVE_ON))
          .thenReturn(resolved(legacyRentalCategory, w2));

      assertThat(service.resolve(4L, null, TransactionDirection.EXPENSE, teaching, EFFECTIVE_ON))
          .isSameAs(legacyRentalCategory);
    }

    @Test
    void rejectsAProfileMappingForAnotherLegacyAlias() {
      FinancialCategory category =
          category(
              4L, "ADVERTISING", TransactionDirection.EXPENSE, TaxTreatment.SCHEDULE_E, "5", true);
      when(financialCategoryRepository.findById(4L)).thenReturn(Optional.of(category));
      when(reportPolicyResolver.resolve(10L, 4L, EFFECTIVE_ON))
          .thenReturn(new ReportPolicyResolution.Incompatible(4L, 40L));

      assertThatThrownBy(
              () -> service.resolve(4L, null, TransactionDirection.EXPENSE, teaching, EFFECTIVE_ON))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("does not match");
    }

    @Test
    void returnsFalseForAnInactiveMappingSoCallersCanChooseADefault() {
      FinancialCategory category =
          category(
              4L, "ADVERTISING", TransactionDirection.EXPENSE, TaxTreatment.SCHEDULE_E, "5", true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(reportPolicyResolver.resolve(10L, 4L, EFFECTIVE_ON))
          .thenReturn(
              new ReportPolicyResolution.Resolved(neutral(category, true), w2, 4L, "5", false));

      assertThat(
              service.isCompatible(category, teaching, TransactionDirection.EXPENSE, EFFECTIVE_ON))
          .isFalse();
    }

    @Test
    void usesTheEffectiveProfileRatherThanTheLegacyActivityColumn() {
      ReportingProfile scheduleC = profile(ReportingProfileKey.SCHEDULE_C, true);
      when(reportPolicyResolver.resolveProfileForPeriod(10L, PERIOD_START, PERIOD_END))
          .thenReturn(new ReportingProfilePeriodResolution.Resolved(scheduleC));

      assertThat(
              service.usesReportingProfile(
                  teaching, TaxTreatment.SCHEDULE_C, PERIOD_START, PERIOD_END))
          .isTrue();
    }

    @Test
    void includesAHistoricalReportMappingAfterTheActivityProfileChanges() {
      FinancialCategory repairs =
          category(
              5L, "REPAIRS", TransactionDirection.EXPENSE, TaxTreatment.SCHEDULE_E, "14", true);
      ReportingProfile scheduleE = profile(ReportingProfileKey.SCHEDULE_E, true);
      when(reportPolicyResolver.resolve(10L, 5L, PERIOD_START))
          .thenReturn(resolved(repairs, scheduleE));
      when(reportPolicyResolver.resolve(10L, 5L, PERIOD_END))
          .thenReturn(resolved(repairs, scheduleE));

      assertThat(
              service.isIncludedInReport(
                  repairs, teaching, TaxTreatment.SCHEDULE_E, PERIOD_START, PERIOD_END))
          .isTrue();
    }

    @Test
    void excludesAReportCategoryMappedToAnotherLegacyAlias() {
      FinancialCategory repairs =
          category(
              5L, "REPAIRS", TransactionDirection.EXPENSE, TaxTreatment.SCHEDULE_E, "14", true);
      when(reportPolicyResolver.resolve(10L, 5L, PERIOD_START))
          .thenReturn(new ReportPolicyResolution.Incompatible(5L, 50L));
      when(reportPolicyResolver.resolve(10L, 5L, PERIOD_END))
          .thenReturn(new ReportPolicyResolution.Incompatible(5L, 50L));

      assertThat(
              service.isIncludedInReport(
                  repairs, teaching, TaxTreatment.SCHEDULE_E, PERIOD_START, PERIOD_END))
          .isFalse();
    }
  }

  private void setMode(ReportPolicyReadMode mode) {
    ReflectionTestUtils.setField(service, "reportPolicyReadMode", mode);
  }

  private FinancialCategory category(
      Long id,
      String key,
      TransactionDirection direction,
      TaxTreatment taxTreatment,
      String taxLine,
      boolean active) {
    return FinancialCategory.builder()
        .id(id)
        .key(key)
        .label(key)
        .direction(direction)
        .taxTreatment(taxTreatment)
        .taxLine(taxLine)
        .active(active)
        .system(true)
        .build();
  }

  private ReportingProfile profile(ReportingProfileKey key, boolean active) {
    return ReportingProfile.builder()
        .id((long) key.ordinal() + 1)
        .key(key)
        .label(key.name())
        .active(active)
        .build();
  }

  private NeutralCategory neutral(FinancialCategory category, boolean active) {
    return NeutralCategory.builder()
        .id(category.getId() + 100)
        .key(category.getKey())
        .label(category.getLabel())
        .direction(category.getDirection())
        .active(active)
        .system(category.isSystem())
        .build();
  }

  private ReportPolicyResolution.Resolved resolved(
      FinancialCategory category, ReportingProfile profile) {
    return new ReportPolicyResolution.Resolved(
        neutral(category, category.isActive()),
        profile,
        category.getId(),
        category.getTaxLine(),
        category.isActive());
  }
}
