package com.bookie.ledger.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.ledger.application.LedgerReportData;
import com.bookie.ledger.application.LedgerReportParityException;
import com.bookie.repository.ActivityCategoryTotalProjection;
import com.bookie.repository.ActivityTotalProjection;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
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

@ExtendWith(MockitoExtension.class)
class LegacyLedgerReportQueryTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 12, 31);

  @Mock private IncomeRepository incomeRepository;
  @Mock private ExpenseRepository expenseRepository;
  @Mock private CategoryCatalog categoryCatalog;

  private LegacyLedgerReportQuery query;

  @BeforeEach
  void setUp() {
    query = new LegacyLedgerReportQuery(incomeRepository, expenseRepository, categoryCatalog);
  }

  @Nested
  class Cashflow {

    @Test
    void wrapsAndOrdersLegacyAggregateQueries() {
      when(incomeRepository.sumByActivityBetween(FROM, TO))
          .thenReturn(List.of(activityTotal(2L, "20.00"), activityTotal(1L, "10.00")));
      when(expenseRepository.sumByActivityBetween(FROM, TO))
          .thenReturn(List.of(activityTotal(1L, "3.00")));

      LedgerReportData.CashflowTotals result = query.cashflow(FROM, TO);

      assertThat(result.incomeTotals())
          .extracting(LedgerReportData.ActivityTotal::activityId)
          .containsExactly(1L, 2L);
      assertThat(result.expenseTotals())
          .containsExactly(new LedgerReportData.ActivityTotal(1L, new BigDecimal("3.00")));
    }
  }

  @Nested
  class ScheduleE {

    @Test
    void addsNeutralCategoryIdentityWithoutChangingTheLegacyCategory() {
      NeutralCategory neutral = NeutralCategory.builder().id(30L).build();
      LegacyCategoryMap mapping =
          LegacyCategoryMap.builder()
              .legacyCategoryId(20L)
              .neutralCategory(neutral)
              .legacyCategoryKey("REPAIRS")
              .build();
      when(incomeRepository.sumByActivityBetween(FROM, TO))
          .thenReturn(List.of(activityTotal(1L, "100.00")));
      when(expenseRepository.sumByActivityAndCategoryBetween(FROM, TO))
          .thenReturn(List.of(categoryTotal(1L, 20L, "25.00")));
      when(categoryCatalog.findByLegacyCategoryId(20L)).thenReturn(Optional.of(mapping));

      LedgerReportData.ScheduleETotals result = query.scheduleE(FROM, TO);

      assertThat(result.expenseTotals())
          .containsExactly(
              LedgerReportData.ActivityCategoryTotal.builder()
                  .activityId(1L)
                  .neutralCategoryId(30L)
                  .legacyCategoryId(20L)
                  .total(new BigDecimal("25.00"))
                  .build());
    }

    @Test
    void failsClosedWhenALegacyCategoryHasNoNeutralMapping() {
      when(expenseRepository.sumByActivityAndCategoryBetween(FROM, TO))
          .thenReturn(List.of(categoryTotal(1L, 20L, "25.00")));
      when(categoryCatalog.findByLegacyCategoryId(20L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> query.scheduleE(FROM, TO))
          .isInstanceOf(LedgerReportParityException.class)
          .hasMessageContaining("missing its neutral mapping");
    }
  }

  private ActivityTotalProjection activityTotal(Long activityId, String total) {
    return new ActivityTotalProjection() {
      @Override
      public Long getActivityId() {
        return activityId;
      }

      @Override
      public BigDecimal getTotal() {
        return new BigDecimal(total);
      }
    };
  }

  private ActivityCategoryTotalProjection categoryTotal(
      Long activityId, Long categoryId, String total) {
    return new ActivityCategoryTotalProjection() {
      @Override
      public Long getActivityId() {
        return activityId;
      }

      @Override
      public Long getCategoryId() {
        return categoryId;
      }

      @Override
      public BigDecimal getTotal() {
        return new BigDecimal(total);
      }
    };
  }
}
