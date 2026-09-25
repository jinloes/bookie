package com.bookie.catalog.financialcategory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.config.ClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({ClockConfiguration.class, JpaFinancialCategoryStore.class})
class JpaFinancialCategoryStoreTest {

  @Autowired private TestEntityManager em;
  @Autowired private JpaFinancialCategoryStore store;

  private FinancialCategory inactive;

  @BeforeEach
  void persistCategories() {
    em.persist(category("RENT", "Rent", TransactionDirection.INCOME, true));
    em.persist(category("UTILITIES", "Utilities", TransactionDirection.EXPENSE, true));
    em.persist(category("ADVERTISING", "Advertising", TransactionDirection.EXPENSE, true));
    inactive = em.persist(category("RETIRED", "Retired", TransactionDirection.EXPENSE, false));
    em.flush();
    em.clear();
  }

  @Nested
  class FindAllActive {

    @Test
    void returnsOnlyActiveCategoriesOrderedByDirectionThenLabel() {
      assertThat(store.findAllActive())
          .extracting(FinancialCategory::getKey)
          .containsExactly("ADVERTISING", "UTILITIES", "RENT");
    }
  }

  @Nested
  class FindById {

    @Test
    void findsACategoryRegardlessOfItsActiveFlag() {
      assertThat(store.findById(inactive.getId()))
          .get()
          .extracting(FinancialCategory::getKey)
          .isEqualTo("RETIRED");
    }

    @Test
    void returnsEmptyForAnUnknownId() {
      assertThat(store.findById(Long.MAX_VALUE)).isEmpty();
    }
  }

  @Nested
  class FindByKey {

    @Test
    void findsACategoryByItsKey() {
      assertThat(store.findByKey("RENT"))
          .get()
          .satisfies(
              category -> {
                assertThat(category.getLabel()).isEqualTo("Rent");
                assertThat(category.getDirection()).isEqualTo(TransactionDirection.INCOME);
              });
    }

    @Test
    void returnsEmptyForAnUnknownKey() {
      assertThat(store.findByKey("NO_SUCH_CATEGORY")).isEmpty();
    }
  }

  private static FinancialCategory category(
      String key, String label, TransactionDirection direction, boolean active) {
    return FinancialCategory.builder()
        .key(key)
        .label(label)
        .direction(direction)
        .taxTreatment(TaxTreatment.NONE)
        .active(active)
        .system(false)
        .build();
  }
}
