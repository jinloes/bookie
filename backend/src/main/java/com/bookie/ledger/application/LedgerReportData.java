package com.bookie.ledger.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

public final class LedgerReportData {

  private LedgerReportData() {}

  public record ActivityTotal(Long activityId, BigDecimal total) {

    public ActivityTotal {
      Objects.requireNonNull(activityId, "activityId");
      Objects.requireNonNull(total, "total");
    }
  }

  @Builder
  public record ActivityCategoryTotal(
      Long activityId, Long neutralCategoryId, Long legacyCategoryId, BigDecimal total) {

    public ActivityCategoryTotal {
      Objects.requireNonNull(activityId, "activityId");
      Objects.requireNonNull(neutralCategoryId, "neutralCategoryId");
      Objects.requireNonNull(legacyCategoryId, "legacyCategoryId");
      Objects.requireNonNull(total, "total");
    }
  }

  public record CashflowTotals(
      List<ActivityTotal> incomeTotals, List<ActivityTotal> expenseTotals) {

    public CashflowTotals {
      incomeTotals = List.copyOf(incomeTotals);
      expenseTotals = List.copyOf(expenseTotals);
    }
  }

  public record ScheduleETotals(
      List<ActivityTotal> incomeTotals, List<ActivityCategoryTotal> expenseTotals) {

    public ScheduleETotals {
      incomeTotals = List.copyOf(incomeTotals);
      expenseTotals = List.copyOf(expenseTotals);
    }
  }
}
