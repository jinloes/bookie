package com.bookie.catalog.counterparty.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.config.ClockConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({ClockConfiguration.class, JpaCounterpartyStore.class})
@ExtendWith(OutputCaptureExtension.class)
class JpaCounterpartyStoreTest {

  @Autowired private TestEntityManager em;
  @Autowired private JpaCounterpartyStore store;

  private Counterparty persist(String name, String... aliases) {
    return em.persistAndFlush(
        Counterparty.builder()
            .name(name)
            .type(CounterpartyType.COMPANY)
            .aliases(new ArrayList<>(List.of(aliases)))
            .build());
  }

  @Nested
  class FindByNameIgnoreCase {

    @Test
    void returnsTheOnlyCaseInsensitiveMatch() {
      Counterparty acme = persist("Acme");
      persist("Other");
      em.clear();

      assertThat(store.findByNameIgnoreCase("ACME"))
          .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(acme.getId()));
    }

    @Test
    void returnsEmptyWhenTwoCounterpartiesShareTheName(CapturedOutput output) {
      persist("Acme");
      persist("ACME");
      em.clear();

      assertThat(store.findByNameIgnoreCase("acme")).isEmpty();
      assertThat(output).contains("Ambiguous counterparty name lookup matched 2 counterparties");
    }
  }

  @Nested
  class FindByAliasIgnoreCase {

    @Test
    void returnsTheOnlyOwner() {
      Counterparty utility = persist("Pacific Gas and Electric Company", "PG&E");
      persist("Other", "OTHER");
      em.clear();

      assertThat(store.findByAliasIgnoreCase("pg&e"))
          .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(utility.getId()));
    }

    @Test
    void returnsTheOwnerWhenItHoldsTheAliasTwiceInDifferentCase() {
      Counterparty water = persist("Alameda County Water District", "ACWD", "acwd");
      em.clear();

      assertThat(store.findByAliasIgnoreCase("Acwd"))
          .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(water.getId()));
    }

    @Test
    void returnsEmptyWhenTwoCounterpartiesShareTheAlias(CapturedOutput output) {
      persist("Jane Doe", "JD");
      persist("John Doe", "jd");
      em.clear();

      assertThat(store.findByAliasIgnoreCase("JD")).isEmpty();
      assertThat(output).contains("Ambiguous counterparty alias lookup matched 2 counterparties");
    }
  }

  @Nested
  class AliasBelongsToAnotherCounterparty {

    @Test
    void isTrueOnlyForADifferentOwner() {
      Counterparty jane = persist("Jane Doe", "JD");
      Counterparty john = persist("John Doe");
      em.clear();

      assertThat(store.aliasBelongsToAnotherCounterparty("jd", john.getId())).isTrue();
      assertThat(store.aliasBelongsToAnotherCounterparty("jd", jane.getId())).isFalse();
      assertThat(store.aliasBelongsToAnotherCounterparty("unknown", john.getId())).isFalse();
    }
  }
}
