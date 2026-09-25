package com.bookie.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.counterparty.infrastructure.CounterpartyRepository;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import com.bookie.catalog.property.infrastructure.PropertyRepository;
import com.bookie.config.ClockConfiguration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(ClockConfiguration.class)
class CatalogRepositoryQueryTest {

  @Autowired private TestEntityManager em;
  @Autowired private CounterpartyRepository payerRepository;
  @Autowired private PropertyRepository propertyRepository;

  @Nested
  class FindByAliasIgnoreCase {

    @Test
    void findByAliasIgnoreCase_matchesCaseInsensitively() {
      Counterparty payer =
          Counterparty.builder()
              .name("Pacific Gas and Electric Company")
              .type(CounterpartyType.COMPANY)
              .aliases(List.of("PG&E"))
              .build();
      em.persistAndFlush(payer);

      assertThat(payerRepository.findByAliasIgnoreCase("pg&e")).isPresent();
      assertThat(payerRepository.findByAliasIgnoreCase("PG&E")).isPresent();
      assertThat(payerRepository.findByAliasIgnoreCase("Pg&E")).isPresent();
    }

    @Test
    void findByAliasIgnoreCase_returnsEmptyForUnknownAlias() {
      assertThat(payerRepository.findByAliasIgnoreCase("nonexistent-alias")).isEmpty();
    }
  }

  @Nested
  class FindByAccountIn {

    @Test
    void findByAccountIn_matchesByNormalizedAccount() {
      // AccountNumbers.normalize lowercases and strips leading mask chars; Payer.normalizeAccounts
      // runs the same normalization on persist — so "ACC-123" is stored as "acc-123".
      Counterparty payer =
          Counterparty.builder()
              .name("Water Authority")
              .type(CounterpartyType.COMPANY)
              .accounts(new java.util.HashSet<>(List.of("ACC-123")))
              .build();
      em.persistAndFlush(payer);

      List<Counterparty> results = payerRepository.findByAccountIn(List.of("acc-123"));

      assertThat(results).hasSize(1);
      assertThat(results.get(0).getName()).isEqualTo("Water Authority");
    }
  }

  @Nested
  class FindPropertyByAccountIn {

    @Test
    void findPropertyByAccountIn_matchesCaseInsensitively() {
      Property property =
          Property.builder()
              .name("Elm Street Duplex")
              .address("789 Elm St, Austin, TX 78701")
              .type(PropertyType.MULTI_FAMILY)
              .accounts(new java.util.HashSet<>(List.of("METER-456")))
              .build();
      em.persistAndFlush(property);

      // Property.normalizeAccounts lowercases on persist, query uses LOWER() as well.
      List<Property> results = propertyRepository.findByAccountIn(List.of("meter-456"));

      assertThat(results).hasSize(1);
      assertThat(results.get(0).getName()).isEqualTo("Elm Street Duplex");
    }
  }
}
