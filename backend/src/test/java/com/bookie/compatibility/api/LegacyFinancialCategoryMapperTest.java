package com.bookie.compatibility.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.model.FinancialCategory;
import com.bookie.model.TransactionDirection;
import org.junit.jupiter.api.Test;

class LegacyFinancialCategoryMapperTest {

  @Test
  void mapsTheRetainedEntityWithoutChangingTheLegacyResponseShape() {
    FinancialCategory category =
        FinancialCategory.builder()
            .id(41L)
            .key("legacy-key")
            .label("Legacy label")
            .direction(TransactionDirection.EXPENSE)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .taxLine("19")
            .active(true)
            .system(false)
            .build();

    assertThat(LegacyFinancialCategoryMapper.toResponse(category))
        .isEqualTo(
            new FinancialCategoryResponse(
                41L,
                "legacy-key",
                "Legacy label",
                TransactionDirection.EXPENSE,
                LegacyTaxTreatment.SCHEDULE_E,
                "19",
                true,
                false));
  }

  @Test
  void preservesANullNestedCategory() {
    assertThat(LegacyFinancialCategoryMapper.toResponse(null)).isNull();
  }
}
