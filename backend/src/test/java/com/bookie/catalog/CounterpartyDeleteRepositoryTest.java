package com.bookie.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.bookie.catalog.classification.domain.EmailKeywordPayerHistory;
import com.bookie.catalog.classification.domain.PayerPropertyHistory;
import com.bookie.catalog.classification.infrastructure.EmailKeywordPayerHistoryRepository;
import com.bookie.catalog.classification.infrastructure.PayerPropertyHistoryRepository;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.counterparty.infrastructure.CounterpartyRepository;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import com.bookie.config.ClockConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(ClockConfiguration.class)
class CounterpartyDeleteRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private CounterpartyRepository payerRepository;
  @Autowired private PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  @Autowired private EmailKeywordPayerHistoryRepository keywordPayerHistoryRepo;

  private Counterparty savePayer() {
    return em.persistAndFlush(
        Counterparty.builder().name("Acme Utilities").type(CounterpartyType.COMPANY).build());
  }

  private Property saveProperty() {
    return em.persistAndFlush(
        Property.builder()
            .name("123 Main St")
            .address("123 Main St, Springfield, CA 90210")
            .type(PropertyType.SINGLE_FAMILY)
            .build());
  }

  @Nested
  class DeleteWithNoRelatedData {

    @Test
    void deleteWithNoRelatedData_succeeds() {
      Counterparty payer = savePayer();
      Long id = payer.getId();

      payerRepository.deleteById(id);

      assertThat(payerRepository.findById(id)).isEmpty();
    }
  }

  @Nested
  class DeleteWithPropertyHistory {

    @Test
    void deleteWithPropertyHistory_deleteByPayerId_thenDeleteSucceeds() {
      Counterparty payer = savePayer();
      Property property = saveProperty();
      Long payerId = payer.getId();

      em.persistAndFlush(
          PayerPropertyHistory.builder().payer(payer).property(property).occurrences(5).build());

      payerPropertyHistoryRepo.deleteByPayerId(payerId);
      em.flush();
      em.clear();

      assertThatNoException().isThrownBy(() -> payerRepository.deleteById(payerId));

      em.flush();
      assertThat(payerRepository.findById(payerId)).isEmpty();
      assertThat(payerPropertyHistoryRepo.findByPayerIdOrderByOccurrencesDesc(payerId)).isEmpty();
    }
  }

  @Nested
  class DeleteWithKeywordPayerHistory {

    @Test
    void deleteWithKeywordPayerHistory_deleteByPayerId_thenDeleteSucceeds() {
      Counterparty payer = savePayer();
      Long payerId = payer.getId();

      em.persistAndFlush(
          EmailKeywordPayerHistory.builder()
              .keyword("acct-7890")
              .payer(payer)
              .occurrences(2)
              .build());

      keywordPayerHistoryRepo.deleteByPayerId(payerId);
      em.flush();
      em.clear();

      assertThatNoException().isThrownBy(() -> payerRepository.deleteById(payerId));

      em.flush();
      assertThat(payerRepository.findById(payerId)).isEmpty();
    }
  }

  @Nested
  class DeleteWithAllRelations {

    @Test
    void deleteWithAllRelations_fullCascadeOrder_succeeds() {
      Counterparty payer = savePayer();
      Property property = saveProperty();
      Long payerId = payer.getId();

      em.persistAndFlush(
          PayerPropertyHistory.builder().payer(payer).property(property).occurrences(4).build());
      em.persistAndFlush(
          EmailKeywordPayerHistory.builder()
              .keyword("ref-abc123")
              .payer(payer)
              .occurrences(7)
              .build());

      payerPropertyHistoryRepo.deleteByPayerId(payerId);
      keywordPayerHistoryRepo.deleteByPayerId(payerId);
      em.flush();
      em.clear();
      payerRepository.deleteById(payerId);

      assertThat(payerRepository.findById(payerId)).isEmpty();
      assertThat(payerPropertyHistoryRepo.findByPayerIdOrderByOccurrencesDesc(payerId)).isEmpty();
    }
  }
}
