package com.bookie.catalog.financialcategory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.application.FinancialCategoryService.CompatibleCategory;
import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
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

  @Mock private CategoryCatalog categoryCatalog;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private ReportPolicyResolver reportPolicyResolver;

  private FinancialCategoryService service;
  private FinancialActivity teaching;

  @Nested
  class DefaultFor {

    @Test
    void resolvesTheDefaultCategoryAtTheClockDate() {
      NeutralCategory category =
          category(3L, "EMPLOYMENT_OTHER_EXPENSE", TransactionDirection.EXPENSE, true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(reportPolicyResolver.resolveProfile(10L, EFFECTIVE_ON))
          .thenReturn(new ReportingProfileResolution.Resolved(w2));
      when(reportPolicyResolver.resolveNeutral(10L, category, EFFECTIVE_ON))
          .thenReturn(resolved(category, w2, null));
      when(categoryCatalog.findByKey("EMPLOYMENT_OTHER_EXPENSE")).thenReturn(Optional.of(category));

      assertThat(service.defaultFor(teaching, TransactionDirection.EXPENSE)).isEqualTo(category);
    }

    @Test
    void resolvesRentalDefaultCategoryWhenActivityIsScheduleE() {
      NeutralCategory category = category(4L, "RENTAL_INCOME", TransactionDirection.INCOME, true);
      ReportingProfile scheduleE = profile(ReportingProfileKey.SCHEDULE_E, true);
      when(reportPolicyResolver.resolveProfile(10L, EFFECTIVE_ON))
          .thenReturn(new ReportingProfileResolution.Resolved(scheduleE));
      when(reportPolicyResolver.resolveNeutral(10L, category, EFFECTIVE_ON))
          .thenReturn(resolved(category, scheduleE, "Rents received"));
      when(categoryCatalog.findByKey("RENTAL_INCOME")).thenReturn(Optional.of(category));

      assertThat(service.defaultFor(teaching, TransactionDirection.INCOME)).isEqualTo(category);
    }
  }

  @BeforeEach
  void setUp() {
    service =
        new FinancialCategoryService(
            Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC),
            categoryCatalog,
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
      NeutralCategory wages = category(1L, "WAGES", TransactionDirection.INCOME, true);
      NeutralCategory educatorExpense =
          category(2L, "EDUCATOR_EXPENSE", TransactionDirection.EXPENSE, true);
      when(categoryCatalog.findAllActive()).thenReturn(List.of(educatorExpense, wages));

      assertThat(service.findCompatible(null, null))
          .containsExactly(
              new CompatibleCategory(educatorExpense, null, null),
              new CompatibleCategory(wages, null, null));
    }

    @Test
    void returnsOnlyPolicyCompatibleCategoriesForAnActivity() {
      NeutralCategory wages = category(1L, "WAGES", TransactionDirection.INCOME, true);
      NeutralCategory rentalIncome =
          category(2L, "RENTAL_INCOME", TransactionDirection.INCOME, true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(activityCatalog.findActiveById(10L)).thenReturn(teaching);
      when(categoryCatalog.findAllActive()).thenReturn(List.of(rentalIncome, wages));
      when(reportPolicyResolver.resolveProfile(10L, EFFECTIVE_ON))
          .thenReturn(new ReportingProfileResolution.Resolved(w2));
      when(reportPolicyResolver.resolveNeutral(10L, wages, EFFECTIVE_ON))
          .thenReturn(resolved(wages, w2, null));
      when(reportPolicyResolver.resolveNeutral(10L, rentalIncome, EFFECTIVE_ON))
          .thenReturn(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING));

      assertThat(service.findCompatible(TransactionDirection.INCOME, 10L))
          .containsExactly(new CompatibleCategory(wages, "W2", null));
    }

    @Test
    void failsClosedWhenCategoryInventoryIsAmbiguous() {
      NeutralCategory wages = category(1L, "WAGES", TransactionDirection.INCOME, true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(activityCatalog.findActiveById(10L)).thenReturn(teaching);
      when(categoryCatalog.findAllActive()).thenReturn(List.of(wages));
      when(reportPolicyResolver.resolveProfile(10L, EFFECTIVE_ON))
          .thenReturn(new ReportingProfileResolution.Resolved(w2));
      when(reportPolicyResolver.resolveNeutral(10L, wages, EFFECTIVE_ON))
          .thenReturn(
              new ReportPolicyResolution.Ambiguous(
                  ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING, 2));

      assertThatThrownBy(() -> service.findCompatible(TransactionDirection.INCOME, 10L))
          .isInstanceOf(ReportPolicyParityException.class);
    }
  }

  @Nested
  class PolicyPlacement {

    @Test
    void acceptsPolicyPlacementByIdOrKey() {
      NeutralCategory category = category(4L, "ADVERTISING", TransactionDirection.EXPENSE, true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(categoryCatalog.findById(4L)).thenReturn(Optional.of(category));
      when(reportPolicyResolver.resolveNeutral(10L, category, EFFECTIVE_ON))
          .thenReturn(resolved(category, w2, "5"));

      assertThat(service.resolve(4L, null, TransactionDirection.EXPENSE, teaching, EFFECTIVE_ON))
          .isSameAs(category);
    }

    @Test
    void rejectsUnmappedCategoryForActivityProfile() {
      NeutralCategory category = category(4L, "ADVERTISING", TransactionDirection.EXPENSE, true);
      when(categoryCatalog.findById(4L)).thenReturn(Optional.of(category));
      when(reportPolicyResolver.resolveNeutral(10L, category, EFFECTIVE_ON))
          .thenReturn(
              new ReportPolicyResolution.Missing(
                  ReportPolicyResolution.Component.CATEGORY_REPORTING_MAPPING));

      assertThatThrownBy(
              () -> service.resolve(4L, null, TransactionDirection.EXPENSE, teaching, EFFECTIVE_ON))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("does not match");
    }

    @Test
    void rejectsInactiveMappingOrDirectionMismatch() {
      NeutralCategory category = category(4L, "ADVERTISING", TransactionDirection.EXPENSE, true);
      ReportingProfile w2 = profile(ReportingProfileKey.W2, true);
      when(categoryCatalog.findById(4L)).thenReturn(Optional.of(category));
      when(reportPolicyResolver.resolveNeutral(10L, category, EFFECTIVE_ON))
          .thenReturn(new ReportPolicyResolution.Resolved(category, w2, "5", false));

      assertThatThrownBy(
              () -> service.resolve(4L, null, TransactionDirection.EXPENSE, teaching, EFFECTIVE_ON))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(
              () -> service.resolve(4L, null, TransactionDirection.INCOME, teaching, EFFECTIVE_ON))
          .isInstanceOf(ResponseStatusException.class);
    }
  }

  private NeutralCategory category(
      Long id, String key, TransactionDirection direction, boolean active) {
    return NeutralCategory.builder()
        .id(id)
        .key(key)
        .label(key)
        .direction(direction)
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

  private ReportPolicyResolution.Resolved resolved(
      NeutralCategory category, ReportingProfile profile, String taxLine) {
    return new ReportPolicyResolution.Resolved(category, profile, taxLine, category.isActive());
  }
}
