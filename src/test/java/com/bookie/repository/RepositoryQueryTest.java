package com.bookie.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class RepositoryQueryTest {

  @Autowired private TestEntityManager em;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private IncomeRepository incomeRepository;
  @Autowired private PayerRepository payerRepository;
  @Autowired private PropertyRepository propertyRepository;

  private Expense saveExpense(BigDecimal amount) {
    return em.persistAndFlush(
        Expense.builder()
            .amount(amount)
            .description("Test expense")
            .date(LocalDate.of(2024, 3, 10))
            .category(ExpenseCategory.REPAIRS)
            .sourceType(ExpenseSource.MANUAL)
            .build());
  }

  private Expense saveExpenseWithPayer(BigDecimal amount, Payer payer) {
    return em.persistAndFlush(
        Expense.builder()
            .amount(amount)
            .description("Payer expense")
            .date(LocalDate.of(2024, 3, 10))
            .category(ExpenseCategory.UTILITIES)
            .sourceType(ExpenseSource.MANUAL)
            .payer(payer)
            .build());
  }

  private Income saveIncome(BigDecimal amount) {
    return em.persistAndFlush(
        Income.builder()
            .amount(amount)
            .description("Rent payment")
            .date(LocalDate.of(2024, 3, 1))
            .source("Tenant")
            .sourceType(ExpenseSource.MANUAL)
            .build());
  }

  private Payer savePayer(String name) {
    return em.persistAndFlush(Payer.builder().name(name).type(PayerType.COMPANY).build());
  }

  @Nested
  class GetTotalExpenses {

    @Test
    void getTotalExpenses_returnsZeroWithNoData() {
      BigDecimal total = expenseRepository.getTotalExpenses();

      assertThat(total.compareTo(BigDecimal.ZERO)).isEqualTo(0);
    }

    @Test
    void getTotalExpenses_sumsAllAmounts() {
      saveExpense(new BigDecimal("100.00"));
      saveExpense(new BigDecimal("250.50"));

      BigDecimal total = expenseRepository.getTotalExpenses();

      assertThat(total.compareTo(new BigDecimal("350.50"))).isEqualTo(0);
    }
  }

  @Nested
  class GetTotalIncome {

    @Test
    void getTotalIncome_returnsZeroWithNoData() {
      BigDecimal total = incomeRepository.getTotalIncome();

      assertThat(total.compareTo(BigDecimal.ZERO)).isEqualTo(0);
    }

    @Test
    void getTotalIncome_sumsAllAmounts() {
      saveIncome(new BigDecimal("1200.00"));
      saveIncome(new BigDecimal("800.75"));

      BigDecimal total = incomeRepository.getTotalIncome();

      assertThat(total.compareTo(new BigDecimal("2000.75"))).isEqualTo(0);
    }
  }

  @Nested
  class ClearPayerById {

    @Test
    void clearPayerById_nullsPayerReferenceOnMatchingExpenses() {
      Payer payer = savePayer("Pacific Gas & Electric");
      Expense expense = saveExpenseWithPayer(new BigDecimal("99.00"), payer);
      Long expenseId = expense.getId();

      expenseRepository.clearPayerById(payer.getId());
      em.flush();
      em.clear();

      Expense reloaded = expenseRepository.findById(expenseId).orElseThrow();
      assertThat(reloaded.getPayer()).isNull();
    }

    @Test
    void clearPayerById_doesNotAffectOtherExpenses() {
      Payer payerA = savePayer("Payer Alpha");
      Payer payerB = savePayer("Payer Beta");
      saveExpenseWithPayer(new BigDecimal("50.00"), payerA);
      Expense expenseB = saveExpenseWithPayer(new BigDecimal("75.00"), payerB);
      Long expenseBId = expenseB.getId();

      expenseRepository.clearPayerById(payerA.getId());
      em.flush();
      em.clear();

      Expense reloaded = expenseRepository.findById(expenseBId).orElseThrow();
      assertThat(reloaded.getPayer()).isNotNull();
      assertThat(reloaded.getPayer().getId()).isEqualTo(payerB.getId());
    }
  }

  @Nested
  class FindByAliasIgnoreCase {

    @Test
    void findByAliasIgnoreCase_matchesCaseInsensitively() {
      Payer payer =
          Payer.builder()
              .name("Pacific Gas and Electric Company")
              .type(PayerType.COMPANY)
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
      Payer payer =
          Payer.builder()
              .name("Water Authority")
              .type(PayerType.COMPANY)
              .accounts(new java.util.HashSet<>(List.of("ACC-123")))
              .build();
      em.persistAndFlush(payer);

      List<Payer> results = payerRepository.findByAccountIn(List.of("acc-123"));

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
