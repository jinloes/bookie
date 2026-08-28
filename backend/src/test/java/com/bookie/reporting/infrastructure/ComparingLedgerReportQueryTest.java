package com.bookie.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.ledger.application.LedgerReportData;
import com.bookie.ledger.application.LedgerReportParityException;
import com.bookie.ledger.application.LedgerReportQuery;
import com.bookie.reporting.application.ReportingReadMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ComparingLedgerReportQueryTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 12, 31);

  @Mock private LedgerReportQuery legacyQuery;
  @Mock private LedgerReportQuery unifiedQuery;

  private ComparingLedgerReportQuery query;

  @BeforeEach
  void setUp() {
    query = new ComparingLedgerReportQuery(legacyQuery, unifiedQuery);
  }

  @Nested
  class Cashflow {

    @Test
    void legacyModeDoesNotReadTheUnifiedProvider() {
      ReflectionTestUtils.setField(query, "readMode", ReportingReadMode.LEGACY);
      LedgerReportData.CashflowTotals legacy = cashflow("10.00", "2.00");
      when(legacyQuery.cashflow(FROM, TO)).thenReturn(legacy);

      assertThat(query.cashflow(FROM, TO)).isSameAs(legacy);
      verifyNoInteractions(unifiedQuery);
    }

    @Test
    void defaultModeUsesUnifiedProviderWithoutReadingLegacyData() {
      LedgerReportData.CashflowTotals unified = cashflow("10.00", "2.00");
      when(unifiedQuery.cashflow(FROM, TO)).thenReturn(unified);

      assertThat(query.cashflow(FROM, TO)).isSameAs(unified);
      verifyNoInteractions(legacyQuery);
    }

    @Test
    void compareModeIgnoresOrderingAndDecimalScaleButReturnsLegacyData() {
      ReflectionTestUtils.setField(query, "readMode", ReportingReadMode.COMPARE);
      LedgerReportData.CashflowTotals legacy =
          new LedgerReportData.CashflowTotals(
              List.of(activityTotal(2L, "1.00"), activityTotal(1L, "10.00")),
              List.of(activityTotal(1L, "2.00")));
      LedgerReportData.CashflowTotals unified =
          new LedgerReportData.CashflowTotals(
              List.of(activityTotal(1L, "10.0"), activityTotal(2L, "1.0")),
              List.of(activityTotal(1L, "2.0")));
      when(legacyQuery.cashflow(FROM, TO)).thenReturn(legacy);
      when(unifiedQuery.cashflow(FROM, TO)).thenReturn(unified);

      assertThat(query.cashflow(FROM, TO)).isSameAs(legacy);
    }

    @Test
    void compareModeRejectsDifferentGrouping() {
      ReflectionTestUtils.setField(query, "readMode", ReportingReadMode.COMPARE);
      when(legacyQuery.cashflow(FROM, TO)).thenReturn(cashflow("10.00", "2.00"));
      when(unifiedQuery.cashflow(FROM, TO))
          .thenReturn(
              new LedgerReportData.CashflowTotals(
                  List.of(activityTotal(2L, "10.00")), List.of(activityTotal(1L, "2.00"))));

      assertThatThrownBy(() -> query.cashflow(FROM, TO))
          .isInstanceOf(LedgerReportParityException.class)
          .hasMessageContaining("grouping");
    }

    @Test
    void compareModeRejectsDifferentMonetaryValues() {
      ReflectionTestUtils.setField(query, "readMode", ReportingReadMode.COMPARE);
      when(legacyQuery.cashflow(FROM, TO)).thenReturn(cashflow("10.00", "2.00"));
      when(unifiedQuery.cashflow(FROM, TO)).thenReturn(cashflow("10.01", "2.00"));

      assertThatThrownBy(() -> query.cashflow(FROM, TO))
          .isInstanceOf(LedgerReportParityException.class)
          .hasMessageContaining("monetary totals");
    }
  }

  @Nested
  class ScheduleE {

    @Test
    void legacyModeDoesNotReadTheUnifiedProvider() {
      ReflectionTestUtils.setField(query, "readMode", ReportingReadMode.LEGACY);
      LedgerReportData.ScheduleETotals legacy =
          new LedgerReportData.ScheduleETotals(List.of(), List.of());
      when(legacyQuery.scheduleE(FROM, TO)).thenReturn(legacy);

      assertThat(query.scheduleE(FROM, TO)).isSameAs(legacy);
      verifyNoInteractions(unifiedQuery);
    }

    @Test
    void defaultModeUsesUnifiedProviderWithoutReadingLegacyData() {
      LedgerReportData.ScheduleETotals unified =
          new LedgerReportData.ScheduleETotals(List.of(), List.of());
      when(unifiedQuery.scheduleE(FROM, TO)).thenReturn(unified);

      assertThat(query.scheduleE(FROM, TO)).isSameAs(unified);
      verifyNoInteractions(legacyQuery);
    }

    @Test
    void compareModeNormalizesCategoryOrderingAndScale() {
      ReflectionTestUtils.setField(query, "readMode", ReportingReadMode.COMPARE);
      LedgerReportData.ScheduleETotals legacy =
          new LedgerReportData.ScheduleETotals(
              List.of(activityTotal(1L, "100.00")),
              List.of(categoryTotal(2L, 21L, 31L, "2.00"), categoryTotal(1L, 20L, 30L, "5.00")));
      LedgerReportData.ScheduleETotals unified =
          new LedgerReportData.ScheduleETotals(
              List.of(activityTotal(1L, "100.0")),
              List.of(categoryTotal(1L, 20L, 30L, "5.0"), categoryTotal(2L, 21L, 31L, "2.0")));
      when(legacyQuery.scheduleE(FROM, TO)).thenReturn(legacy);
      when(unifiedQuery.scheduleE(FROM, TO)).thenReturn(unified);

      assertThat(query.scheduleE(FROM, TO)).isSameAs(legacy);
    }

    @Test
    void compareModeRejectsDifferentCategoryIdentity() {
      ReflectionTestUtils.setField(query, "readMode", ReportingReadMode.COMPARE);
      when(legacyQuery.scheduleE(FROM, TO))
          .thenReturn(
              new LedgerReportData.ScheduleETotals(
                  List.of(), List.of(categoryTotal(1L, 20L, 30L, "5.00"))));
      when(unifiedQuery.scheduleE(FROM, TO))
          .thenReturn(
              new LedgerReportData.ScheduleETotals(
                  List.of(), List.of(categoryTotal(1L, 20L, 31L, "5.00"))));

      assertThatThrownBy(() -> query.scheduleE(FROM, TO))
          .isInstanceOf(LedgerReportParityException.class)
          .hasMessageContaining("grouping");
    }
  }

  private LedgerReportData.CashflowTotals cashflow(String income, String expense) {
    return new LedgerReportData.CashflowTotals(
        List.of(activityTotal(1L, income)), List.of(activityTotal(1L, expense)));
  }

  private LedgerReportData.ActivityTotal activityTotal(Long activityId, String amount) {
    return new LedgerReportData.ActivityTotal(activityId, new BigDecimal(amount));
  }

  private LedgerReportData.ActivityCategoryTotal categoryTotal(
      Long activityId, Long neutralCategoryId, Long legacyCategoryId, String amount) {
    return LedgerReportData.ActivityCategoryTotal.builder()
        .activityId(activityId)
        .neutralCategoryId(neutralCategoryId)
        .legacyCategoryId(legacyCategoryId)
        .total(new BigDecimal(amount))
        .build();
  }
}
