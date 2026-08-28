package com.bookie.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.application.ReportPolicyReadMode;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolution;
import com.bookie.catalog.reportpolicy.application.ReportPolicyResolver;
import com.bookie.catalog.reportpolicy.application.ReportingProfilePeriodResolution;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import com.bookie.ledger.application.LedgerReportData;
import com.bookie.ledger.application.LedgerReportQuery;
import com.bookie.model.TransactionDirection;
import com.bookie.reporting.domain.ReportResults;
import java.math.BigDecimal;
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
class ReportServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 12, 31);

  @Mock private LedgerReportQuery ledgerReportQuery;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private CategoryCatalog categoryCatalog;
  @Mock private ReportPolicyResolver reportPolicyResolver;

  private ReportService reportService;
  private HouseholdMember owner;
  private FinancialActivity teaching;
  private FinancialActivity tutoring;
  private FinancialActivity rental;

  @BeforeEach
  void setUp() {
    reportService =
        new ReportService(
            ledgerReportQuery, activityCatalog, categoryCatalog, reportPolicyResolver);
    owner = HouseholdMember.builder().id(1L).name("Alex").active(true).build();
    teaching = activity(10L, "Teaching", ActivityType.EMPLOYMENT, TaxTreatment.W2, owner);
    tutoring =
        activity(11L, "Tutoring", ActivityType.SELF_EMPLOYMENT, TaxTreatment.SCHEDULE_C, owner);
    rental = activity(12L, "Oak Street", ActivityType.RENTAL, TaxTreatment.SCHEDULE_E, owner);
  }

  @Nested
  class Cashflow {

    @Test
    void combinesAndSortsTotalsWithoutClampingNegativeNet() {
      when(ledgerReportQuery.cashflow(FROM, TO))
          .thenReturn(
              new LedgerReportData.CashflowTotals(
                  List.of(activityTotal(10L, "2400.00"), activityTotal(11L, "600.00")),
                  List.of(activityTotal(10L, "2600.00"), activityTotal(11L, "100.00"))));
      when(activityCatalog.findAll()).thenReturn(List.of(tutoring, teaching));

      ReportResults.Cashflow report = reportService.cashflow(FROM, TO);

      assertThat(report.totalIncome()).isEqualByComparingTo("3000.00");
      assertThat(report.totalExpenses()).isEqualByComparingTo("2700.00");
      assertThat(report.netCashflow()).isEqualByComparingTo("300.00");
      assertThat(report.activities())
          .extracting(row -> row.activity().getName())
          .containsExactly("Teaching", "Tutoring");
      assertThat(report.activities().getFirst().netCashflow()).isEqualByComparingTo("-200.00");
    }

    @Test
    void filtersByOwnerAndActivityAndHandlesEmptyData() {
      HouseholdMember otherOwner =
          HouseholdMember.builder().id(2L).name("Jordan").active(true).build();
      tutoring.setOwner(otherOwner);
      when(ledgerReportQuery.cashflow(FROM, TO))
          .thenReturn(
              new LedgerReportData.CashflowTotals(
                  List.of(activityTotal(10L, "20.00"), activityTotal(11L, "5.00")),
                  List.of(activityTotal(10L, "2.00"))));
      when(activityCatalog.findAll()).thenReturn(List.of(teaching, tutoring));

      ReportResults.Cashflow report =
          reportService.cashflow(FROM, TO, owner.getId(), teaching.getId());

      assertThat(report.totalIncome()).isEqualByComparingTo("20.00");
      assertThat(report.activities())
          .singleElement()
          .satisfies(row -> assertThat(row.activity()).isSameAs(teaching));
    }

    @Test
    void rejectsInvalidPeriodsAndMissingActivities() {
      assertThatThrownBy(() -> reportService.cashflow(null, TO))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(() -> reportService.cashflow(FROM, null))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(() -> reportService.cashflow(TO, FROM))
          .isInstanceOf(ResponseStatusException.class);

      when(ledgerReportQuery.cashflow(FROM, TO))
          .thenReturn(
              new LedgerReportData.CashflowTotals(List.of(activityTotal(99L, "1.00")), List.of()));
      when(activityCatalog.findAll()).thenReturn(List.of());
      assertThatThrownBy(() -> reportService.cashflow(FROM, TO))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("missing activity");
    }
  }

  @Nested
  class ScheduleE {

    @Test
    void groupsIncludedLegacyCategoriesAndPreservesNegativeNet() {
      ReflectionTestUtils.setField(
          reportService, "reportPolicyReadMode", ReportPolicyReadMode.LEGACY);
      LegacyCategoryView repairs = category(20L, TaxTreatment.SCHEDULE_E, "14");
      LegacyCategoryView educatorExpense = category(21L, TaxTreatment.W2, "Schedule 1");
      stubScheduleTotals();
      when(activityCatalog.findAll()).thenReturn(List.of(rental, teaching));
      when(categoryCatalog.findLegacyCategoryById(20L)).thenReturn(Optional.of(repairs));
      when(categoryCatalog.findLegacyCategoryById(21L)).thenReturn(Optional.of(educatorExpense));

      ReportResults.ScheduleE report = reportService.scheduleE(2026);

      assertThat(report.rentalIncome()).isEqualByComparingTo("1000.00");
      assertThat(report.expenses()).isEqualByComparingTo("1200.00");
      assertThat(report.netIncome()).isEqualByComparingTo("-200.00");
      assertThat(report.activities())
          .singleElement()
          .satisfies(
              row ->
                  assertThat(row.categories())
                      .singleElement()
                      .satisfies(total -> assertThat(total.reportLine()).isEqualTo("14")));
    }

    @Test
    void includesEmptyRentalActivitiesAndRejectsInvalidYears() {
      ReflectionTestUtils.setField(
          reportService, "reportPolicyReadMode", ReportPolicyReadMode.LEGACY);
      when(ledgerReportQuery.scheduleE(FROM, TO))
          .thenReturn(new LedgerReportData.ScheduleETotals(List.of(), List.of()));
      when(activityCatalog.findAll()).thenReturn(List.of(rental));

      assertThat(reportService.scheduleE(2026).activities())
          .singleElement()
          .satisfies(
              row -> {
                assertThat(row.rentalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(row.expenses()).isEqualByComparingTo(BigDecimal.ZERO);
              });
      assertThatThrownBy(() -> reportService.scheduleE(1899))
          .isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(() -> reportService.scheduleE(10000))
          .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void failsClosedWhenTheLedgerReferencesAMissingCategory() {
      ReflectionTestUtils.setField(
          reportService, "reportPolicyReadMode", ReportPolicyReadMode.LEGACY);
      when(ledgerReportQuery.scheduleE(FROM, TO))
          .thenReturn(
              new LedgerReportData.ScheduleETotals(
                  List.of(), List.of(categoryTotal(12L, 30L, 99L, "10.00"))));
      when(activityCatalog.findAll()).thenReturn(List.of(rental));

      assertThatThrownBy(() -> reportService.scheduleE(2026))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("missing category");
    }

    @Test
    void defaultPolicyModeUsesEffectiveProfileAndReportLine() {
      FinancialActivity newlyRental =
          activity(12L, "Oak Street", ActivityType.RENTAL, TaxTreatment.W2, owner);
      LegacyCategoryView category = category(20L, TaxTreatment.W2, "legacy-line");
      NeutralCategory neutral = neutralCategory();
      ReportingProfile profile = profile(ReportingProfileKey.SCHEDULE_E);
      when(ledgerReportQuery.scheduleE(FROM, TO))
          .thenReturn(
              new LedgerReportData.ScheduleETotals(
                  List.of(activityTotal(12L, "100.00")),
                  List.of(categoryTotal(12L, 30L, 20L, "25.00"))));
      when(activityCatalog.findAll()).thenReturn(List.of(newlyRental));
      when(categoryCatalog.findLegacyCategoryById(20L)).thenReturn(Optional.of(category));
      when(reportPolicyResolver.resolveProfileForPeriod(12L, FROM, TO))
          .thenReturn(new ReportingProfilePeriodResolution.Resolved(profile));
      when(reportPolicyResolver.resolve(12L, 20L, FROM))
          .thenReturn(resolved(neutral, profile, "effective-line"));
      when(reportPolicyResolver.resolve(12L, 20L, TO))
          .thenReturn(resolved(neutral, profile, "effective-line"));

      ReportResults.ScheduleE report = reportService.scheduleE(2026);

      assertThat(report.activities().getFirst().categories().getFirst().reportLine())
          .isEqualTo("effective-line");
    }

    @Test
    void comparisonModeReturnsLegacyOutputAfterExactPolicyParity() {
      ReflectionTestUtils.setField(
          reportService, "reportPolicyReadMode", ReportPolicyReadMode.COMPARE);
      LegacyCategoryView repairs = category(20L, TaxTreatment.SCHEDULE_E, "14");
      NeutralCategory neutral = neutralCategory();
      ReportingProfile profile = profile(ReportingProfileKey.SCHEDULE_E);
      when(ledgerReportQuery.scheduleE(FROM, TO))
          .thenReturn(
              new LedgerReportData.ScheduleETotals(
                  List.of(), List.of(categoryTotal(12L, 30L, 20L, "25.00"))));
      when(activityCatalog.findAll()).thenReturn(List.of(rental));
      when(categoryCatalog.findLegacyCategoryById(20L)).thenReturn(Optional.of(repairs));
      when(reportPolicyResolver.resolveProfileForPeriod(12L, FROM, TO))
          .thenReturn(new ReportingProfilePeriodResolution.Resolved(profile));
      when(reportPolicyResolver.resolve(12L, 20L, FROM))
          .thenReturn(resolved(neutral, profile, "14"));
      when(reportPolicyResolver.resolve(12L, 20L, TO)).thenReturn(resolved(neutral, profile, "14"));

      ReportResults.ScheduleE report = reportService.scheduleE(2026);

      assertThat(report.expenses()).isEqualByComparingTo("25.00");
      assertThat(report.activities().getFirst().categories().getFirst().reportLine())
          .isEqualTo("14");
    }

    @Test
    void comparisonModeFailsClosedOnProfileOrMappingDrift() {
      ReflectionTestUtils.setField(
          reportService, "reportPolicyReadMode", ReportPolicyReadMode.COMPARE);
      when(ledgerReportQuery.scheduleE(FROM, TO))
          .thenReturn(new LedgerReportData.ScheduleETotals(List.of(), List.of()));
      when(activityCatalog.findAll()).thenReturn(List.of(rental));
      when(reportPolicyResolver.resolveProfileForPeriod(12L, FROM, TO))
          .thenReturn(
              new ReportingProfilePeriodResolution.Resolved(
                  profile(ReportingProfileKey.SCHEDULE_C)));

      assertThatThrownBy(() -> reportService.scheduleE(2026))
          .isInstanceOf(ReportPolicyParityException.class)
          .hasMessageContaining("differs");
    }

    @Test
    void newPolicyModeFailsClosedWhenAMappingChangesWithinThePeriod() {
      ReflectionTestUtils.setField(reportService, "reportPolicyReadMode", ReportPolicyReadMode.NEW);
      LegacyCategoryView repairs = category(20L, TaxTreatment.SCHEDULE_E, "14");
      NeutralCategory neutral = neutralCategory();
      ReportingProfile profile = profile(ReportingProfileKey.SCHEDULE_E);
      when(ledgerReportQuery.scheduleE(FROM, TO))
          .thenReturn(
              new LedgerReportData.ScheduleETotals(
                  List.of(), List.of(categoryTotal(12L, 30L, 20L, "25.00"))));
      when(activityCatalog.findAll()).thenReturn(List.of(rental));
      when(categoryCatalog.findLegacyCategoryById(20L)).thenReturn(Optional.of(repairs));
      when(reportPolicyResolver.resolveProfileForPeriod(12L, FROM, TO))
          .thenReturn(new ReportingProfilePeriodResolution.Resolved(profile));
      when(reportPolicyResolver.resolve(12L, 20L, FROM))
          .thenReturn(resolved(neutral, profile, "14"));
      when(reportPolicyResolver.resolve(12L, 20L, TO)).thenReturn(resolved(neutral, profile, "15"));

      assertThatThrownBy(() -> reportService.scheduleE(2026))
          .isInstanceOf(ReportPolicyParityException.class)
          .hasMessageContaining("changes within");
    }
  }

  private void stubScheduleTotals() {
    when(ledgerReportQuery.scheduleE(FROM, TO))
        .thenReturn(
            new LedgerReportData.ScheduleETotals(
                List.of(activityTotal(12L, "1000.00")),
                List.of(
                    categoryTotal(12L, 30L, 20L, "1200.00"),
                    categoryTotal(12L, 31L, 21L, "500.00"))));
  }

  private FinancialActivity activity(
      Long id,
      String name,
      ActivityType activityType,
      TaxTreatment taxTreatment,
      HouseholdMember activityOwner) {
    return FinancialActivity.builder()
        .id(id)
        .name(name)
        .activityType(activityType)
        .taxTreatment(taxTreatment)
        .owner(activityOwner)
        .active(true)
        .build();
  }

  private LegacyCategoryView category(Long id, TaxTreatment taxTreatment, String taxLine) {
    return LegacyCategoryView.builder()
        .id(id)
        .key("CATEGORY_" + id)
        .label("Category " + id)
        .direction(TransactionDirection.EXPENSE)
        .taxTreatment(LegacyTaxTreatment.valueOf(taxTreatment.name()))
        .taxLine(taxLine)
        .active(true)
        .system(true)
        .build();
  }

  private NeutralCategory neutralCategory() {
    return NeutralCategory.builder()
        .id(30L)
        .direction(TransactionDirection.EXPENSE)
        .active(true)
        .build();
  }

  private ReportingProfile profile(ReportingProfileKey key) {
    return ReportingProfile.builder().id(40L).key(key).active(true).build();
  }

  private ReportPolicyResolution.Resolved resolved(
      NeutralCategory neutral, ReportingProfile profile, String reportLine) {
    return new ReportPolicyResolution.Resolved(neutral, profile, 20L, reportLine, true);
  }

  private LedgerReportData.ActivityTotal activityTotal(Long activityId, String total) {
    return new LedgerReportData.ActivityTotal(activityId, new BigDecimal(total));
  }

  private LedgerReportData.ActivityCategoryTotal categoryTotal(
      Long activityId, Long neutralCategoryId, Long legacyCategoryId, String total) {
    return LedgerReportData.ActivityCategoryTotal.builder()
        .activityId(activityId)
        .neutralCategoryId(neutralCategoryId)
        .legacyCategoryId(legacyCategoryId)
        .total(new BigDecimal(total))
        .build();
  }
}
