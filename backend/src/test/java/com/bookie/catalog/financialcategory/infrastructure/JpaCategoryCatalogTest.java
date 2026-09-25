package com.bookie.catalog.financialcategory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaCategoryCatalogTest {

  @Mock private EntityManager entityManager;

  @Nested
  class FindByLegacyCategoryId {

    @Test
    void findsTheNeutralMapping() {
      LegacyCategoryMap mapping = LegacyCategoryMap.builder().legacyCategoryId(10L).build();
      when(entityManager.find(LegacyCategoryMap.class, 10L)).thenReturn(mapping);
      JpaCategoryCatalog catalog = new JpaCategoryCatalog(entityManager);

      assertThat(catalog.findByLegacyCategoryId(10L)).contains(mapping);
    }
  }

  @Nested
  class FindLegacyCategoryById {

    @Test
    void findsTheCompatibilityCategory() {
      FinancialCategory category =
          FinancialCategory.builder()
              .id(10L)
              .key("REPAIRS")
              .label("Repairs")
              .direction(TransactionDirection.EXPENSE)
              .taxTreatment(TaxTreatment.SCHEDULE_E)
              .taxLine("14")
              .active(true)
              .system(true)
              .build();
      when(entityManager.find(FinancialCategory.class, 10L)).thenReturn(category);
      when(entityManager.find(LegacyCategoryMap.class, 10L))
          .thenReturn(
              LegacyCategoryMap.builder()
                  .legacyCategoryId(10L)
                  .neutralCategory(NeutralCategory.builder().id(70L).build())
                  .build());
      JpaCategoryCatalog catalog = new JpaCategoryCatalog(entityManager);

      assertThat(catalog.findLegacyCategoryById(10L))
          .contains(
              LegacyCategoryView.builder()
                  .id(10L)
                  .key("REPAIRS")
                  .label("Repairs")
                  .direction(TransactionDirection.EXPENSE)
                  .taxTreatment(LegacyTaxTreatment.SCHEDULE_E)
                  .taxLine("14")
                  .active(true)
                  .system(true)
                  .neutralCategoryId(70L)
                  .build());
    }

    @Test
    void leavesTheNeutralCategoryEmptyWhenTheMappingIsMissing() {
      when(entityManager.find(FinancialCategory.class, 11L))
          .thenReturn(
              FinancialCategory.builder()
                  .id(11L)
                  .key("CUSTOM")
                  .label("Custom")
                  .direction(TransactionDirection.EXPENSE)
                  .taxTreatment(TaxTreatment.NONE)
                  .build());
      JpaCategoryCatalog catalog = new JpaCategoryCatalog(entityManager);

      assertThat(catalog.findLegacyCategoryById(11L))
          .hasValueSatisfying(view -> assertThat(view.neutralCategoryId()).isNull());
    }

    @Test
    void returnsEmptyWhenTheCategoryIsMissing() {
      JpaCategoryCatalog catalog = new JpaCategoryCatalog(entityManager);

      assertThat(catalog.findLegacyCategoryById(10L)).isEmpty();
    }
  }
}
