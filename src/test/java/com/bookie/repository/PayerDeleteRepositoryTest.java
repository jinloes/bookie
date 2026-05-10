package com.bookie.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.bookie.model.EmailKeywordPayerHistory;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Payer;
import com.bookie.model.PayerCategoryHistory;
import com.bookie.model.PayerPropertyHistory;
import com.bookie.model.PayerType;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class PayerDeleteRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private PayerRepository payerRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private PayerCategoryHistoryRepository payerCategoryHistoryRepo;
  @Autowired private PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  @Autowired private EmailKeywordPayerHistoryRepository keywordPayerHistoryRepo;

  private Payer savePayer() {
    return em.persistAndFlush(
        Payer.builder().name("Acme Utilities").type(PayerType.COMPANY).build());
  }

  private Property saveProperty() {
    return em.persistAndFlush(
        Property.builder()
            .name("123 Main St")
            .address("123 Main St, Springfield, CA 90210")
            .type(PropertyType.SINGLE_FAMILY)
            .build());
  }

  private Expense saveExpenseWithPayer(Payer payer) {
    return em.persistAndFlush(
        Expense.builder()
            .amount(new BigDecimal("150.00"))
            .description("Electricity bill")
            .date(LocalDate.of(2024, 1, 15))
            .category(ExpenseCategory.UTILITIES)
            .sourceType(ExpenseSource.MANUAL)
            .payer(payer)
            .build());
  }

  @Nested
  class DeleteWithNoRelatedData {

    @Test
    void deleteWithNoRelatedData_succeeds() {
      Payer payer = savePayer();
      Long id = payer.getId();

      payerRepository.deleteById(id);

      assertThat(payerRepository.findById(id)).isEmpty();
    }
  }

  @Nested
  class DeleteWithLinkedExpense {

    @Test
    void deleteWithLinkedExpense_clearPayerById_thenDeleteSucceeds() {
      Payer payer = savePayer();
      Expense expense = saveExpenseWithPayer(payer);
      Long payerId = payer.getId();
      Long expenseId = expense.getId();

      expenseRepository.clearPayerById(payerId);
      em.flush();
      em.clear();

      assertThatNoException().isThrownBy(() -> payerRepository.deleteById(payerId));

      em.flush();
      em.clear();

      Expense reloaded = expenseRepository.findById(expenseId).orElseThrow();
      assertThat(reloaded.getPayer()).isNull();
      assertThat(payerRepository.findById(payerId)).isEmpty();
    }
  }

  @Nested
  class DeleteWithCategoryHistory {

    @Test
    void deleteWithCategoryHistory_deleteByPayerId_thenDeleteSucceeds() {
      Payer payer = savePayer();
      Long payerId = payer.getId();

      em.persistAndFlush(
          PayerCategoryHistory.builder()
              .payer(payer)
              .category(ExpenseCategory.UTILITIES)
              .occurrences(3)
              .build());

      payerCategoryHistoryRepo.deleteByPayerId(payerId);
      em.flush();
      em.clear();

      assertThatNoException().isThrownBy(() -> payerRepository.deleteById(payerId));

      em.flush();
      assertThat(payerRepository.findById(payerId)).isEmpty();
      assertThat(payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(payerId)).isEmpty();
    }
  }

  @Nested
  class DeleteWithPropertyHistory {

    @Test
    void deleteWithPropertyHistory_deleteByPayerId_thenDeleteSucceeds() {
      Payer payer = savePayer();
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
      Payer payer = savePayer();
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
      Payer payer = savePayer();
      Property property = saveProperty();
      Long payerId = payer.getId();

      saveExpenseWithPayer(payer);
      em.persistAndFlush(
          PayerCategoryHistory.builder()
              .payer(payer)
              .category(ExpenseCategory.REPAIRS)
              .occurrences(1)
              .build());
      em.persistAndFlush(
          PayerPropertyHistory.builder().payer(payer).property(property).occurrences(4).build());
      em.persistAndFlush(
          EmailKeywordPayerHistory.builder()
              .keyword("ref-abc123")
              .payer(payer)
              .occurrences(7)
              .build());

      // Full sequence matching PayerService.delete()
      expenseRepository.clearPayerById(payerId);
      payerCategoryHistoryRepo.deleteByPayerId(payerId);
      payerPropertyHistoryRepo.deleteByPayerId(payerId);
      keywordPayerHistoryRepo.deleteByPayerId(payerId);
      // Flush bulk-update statements before deleteById so Hibernate's removal tracking
      // operates on a clean first-level cache with no stale state.
      em.flush();
      em.clear();
      payerRepository.deleteById(payerId);

      assertThat(payerRepository.findById(payerId)).isEmpty();
      assertThat(payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(payerId)).isEmpty();
      assertThat(payerPropertyHistoryRepo.findByPayerIdOrderByOccurrencesDesc(payerId)).isEmpty();
    }
  }
}
