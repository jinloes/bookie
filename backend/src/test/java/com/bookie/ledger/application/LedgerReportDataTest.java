package com.bookie.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LedgerReportDataTest {

  @Nested
  class CashflowTotals {

    @Test
    void defensivelyCopiesItsResultLists() {
      List<LedgerReportData.ActivityTotal> source =
          new ArrayList<>(List.of(new LedgerReportData.ActivityTotal(1L, BigDecimal.ONE)));

      LedgerReportData.CashflowTotals totals =
          new LedgerReportData.CashflowTotals(source, List.of());
      source.clear();

      assertThat(totals.incomeTotals()).hasSize(1);
      assertThatThrownBy(() -> totals.incomeTotals().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }
}
