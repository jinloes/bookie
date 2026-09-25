package com.bookie.catalog.financialcategory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.catalog.reportpolicy.application.ReportingProfilePeriodResolution;
import com.bookie.catalog.reportpolicy.application.ReportingProfileResolution;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FinancialCategoryServiceTest {

  private static final LocalDate EFFECTIVE_ON = LocalDate.of(2026, 6, 15);
  private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
  private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

  @Mock private FinancialCategoryStore financialCategoryStore;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private ReportPolicyResolver reportPolicyResolver;

  private FinancialCategoryService service;
  private FinancialActivity teaching;

  @Nested
  class DefaultFor {

    @Test
    void resolvesTheDefaultCategoryAtTheClockDate() {
      FinancialCategory category =
          category(
              3L,
              "EMPLOYMENT_OTHER_EXPENSE",
              TransactionDirection.EXPENSE,
              TaxTreatment.W2,
              null,
              true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(reportPolicyResolver.resolveProfile(10L, EFFECTIVE_ON))
          .thenReturn(new ReportingProfileResolution.Resolved(w2));
      when(reportPolicyResolver.resolve(10L, 3L, EFFECTIVE_ON)).thenReturn(resolved(category, w2));
      when(financialCategoryStore.findByKey("EMPLOYMENT_OTHER_EXPENSE"))
          .thenReturn(Optional.of(category));

      assertThat(service.defaultFor(teaching, TransactionDirection.EXPENSE)).isEqualTo(category);
    }
  }

  @BeforeEach
  void setUp() {
    service =
        new FinancialCategoryService(
            Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC),
            financialCategoryStore,
            activityCatalog,
            reportPolicyResolver);
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
  class PolicyResolution {

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
      when(financialCategoryStore.findAllActive()).thenReturn(List.of(educatorExpense, wages));

      assertThat(service.findCompatible(null, null)).containsExactly(educatorExpense, wages);
    }

    @Test
    void treatsANullCategoryAsIncompatibleForFallbackSelection() {
      assertThat(service.isCompatible(null, teaching, TransactionDirection.EXPENSE, EFFECTIVE_ON))
          .isFalse();
    }

    @Test
    void returnsOnlyPolicyCompatibleCategoriesForAnActivity() {
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
      LocalDate today = EFFECTIVE_ON;
      when(activityCatalog.findActiveById(10L)).thenReturn(teaching);
      when(financialCategoryStore.findAllActive()).thenReturn(List.of(rentalIncome, wages));
      when(reportPolicyResolver.resolveProfile(10L, today))
          .thenReturn(new ReportingProfileResolution.Resolved(w2));
      when(reportPolicyResolver.resolve(10L, 1L, today)).thenReturn(resolved(wages, w2));
      when(reportPolicyResolver.resolve(10L, 2L, today))
          .thenReturn(new ReportPolicyResolution.Incompatible(2L, 1L));

      assertThat(service.findCompatible(TransactionDirection.INCOME, 10L)).containsExactly(wages);
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
    void excludesAnIncompatibleReportCategory() {
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
  }

  @Nested
  class PolicyPlacement {

    @Test
    void acceptsPolicyPlacementIndependentOfTheLegacyTaxTreatmentColumn() {
      FinancialCategory legacyRentalCategory =
          category(
              4L, "ADVERTISING", TransactionDirection.EXPENSE, TaxTreatment.SCHEDULE_E, "5", true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(financialCategoryStore.findById(4L)).thenReturn(Optional.of(legacyRentalCategory));
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
      when(financialCategoryStore.findById(4L)).thenReturn(Optional.of(category));
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
