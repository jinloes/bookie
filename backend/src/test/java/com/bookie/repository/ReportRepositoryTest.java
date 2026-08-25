package com.bookie.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.model.ActivityType;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.HouseholdMember;
import com.bookie.model.Income;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest(properties = "spring.flyway.enabled=false")
class ReportRepositoryTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private IncomeRepository incomeRepository;
  @Autowired private ExpenseRepository expenseRepository;

  @Test
  void aggregationProjectionsGroupByActivityAndCategoryWithinPeriod() {
    HouseholdMember owner =
        entityManager.persist(HouseholdMember.builder().name("Alex").active(true).build());
    FinancialActivity rental =
        entityManager.persist(
            FinancialActivity.builder()
                .name("Oak Street")
                .activityType(ActivityType.RENTAL)
                .taxTreatment(TaxTreatment.SCHEDULE_E)
                .owner(owner)
                .active(true)
                .build());
    FinancialActivity teaching =
        entityManager.persist(
            FinancialActivity.builder()
                .name("Teaching")
                .activityType(ActivityType.EMPLOYMENT)
                .taxTreatment(TaxTreatment.W2)
                .owner(owner)
                .active(true)
                .build());
    FinancialActivity tutoring =
        entityManager.persist(
            FinancialActivity.builder()
                .name("Tutoring")
                .activityType(ActivityType.SELF_EMPLOYMENT)
                .taxTreatment(TaxTreatment.SCHEDULE_C)
                .owner(owner)
                .active(true)
                .build());
    FinancialCategory rentalIncome =
        entityManager.persist(
            FinancialCategory.builder()
                .key("TEST_RENTAL_INCOME")
                .label("Rental income")
                .direction(TransactionDirection.INCOME)
                .taxTreatment(TaxTreatment.SCHEDULE_E)
                .active(true)
                .system(true)
                .build());
    FinancialCategory repairs =
        entityManager.persist(
            FinancialCategory.builder()
                .key("TEST_REPAIRS")
                .label("Repairs")
                .direction(TransactionDirection.EXPENSE)
                .taxTreatment(TaxTreatment.SCHEDULE_E)
                .active(true)
                .system(true)
                .build());
    FinancialCategory wages =
        entityManager.persist(
            FinancialCategory.builder()
                .key("TEST_WAGES")
                .label("Wages")
                .direction(TransactionDirection.INCOME)
                .taxTreatment(TaxTreatment.W2)
                .active(true)
                .system(true)
                .build());
    FinancialCategory educatorExpense =
        entityManager.persist(
            FinancialCategory.builder()
                .key("TEST_EDUCATOR_EXPENSE")
                .label("Educator expense")
                .direction(TransactionDirection.EXPENSE)
                .taxTreatment(TaxTreatment.W2)
                .active(true)
                .system(true)
                .build());
    FinancialCategory serviceIncome =
        entityManager.persist(
            FinancialCategory.builder()
                .key("TEST_SERVICE_INCOME")
                .label("Service income")
                .direction(TransactionDirection.INCOME)
                .taxTreatment(TaxTreatment.SCHEDULE_C)
                .active(true)
                .system(true)
                .build());
    FinancialCategory businessSupplies =
        entityManager.persist(
            FinancialCategory.builder()
                .key("TEST_BUSINESS_SUPPLIES")
                .label("Business supplies")
                .direction(TransactionDirection.EXPENSE)
                .taxTreatment(TaxTreatment.SCHEDULE_C)
                .active(true)
                .system(true)
                .build());
    entityManager.persist(
        Income.builder()
            .amount(new BigDecimal("1200.00"))
            .description("Rent")
            .date(LocalDate.of(2026, 2, 1))
            .activity(rental)
            .financialCategory(rentalIncome)
            .build());
    entityManager.persist(
        Expense.builder()
            .amount(new BigDecimal("250.00"))
            .description("Repair")
            .date(LocalDate.of(2026, 3, 1))
            .category(ExpenseCategory.REPAIRS)
            .activity(rental)
            .financialCategory(repairs)
            .build());
    entityManager.persist(
        Income.builder()
            .amount(new BigDecimal("2400.10"))
            .description("Paycheck")
            .date(LocalDate.of(2026, 2, 15))
            .activity(teaching)
            .financialCategory(wages)
            .build());
    entityManager.persist(
        Expense.builder()
            .amount(new BigDecimal("125.03"))
            .description("Classroom supplies")
            .date(LocalDate.of(2026, 3, 15))
            .category(ExpenseCategory.OTHER)
            .activity(teaching)
            .financialCategory(educatorExpense)
            .build());
    entityManager.persist(
        Income.builder()
            .amount(new BigDecimal("600.05"))
            .description("Tutoring session")
            .date(LocalDate.of(2026, 4, 1))
            .activity(tutoring)
            .financialCategory(serviceIncome)
            .build());
    entityManager.persist(
        Expense.builder()
            .amount(new BigDecimal("75.02"))
            .description("Tutoring materials")
            .date(LocalDate.of(2026, 4, 2))
            .category(ExpenseCategory.SUPPLIES)
            .activity(tutoring)
            .financialCategory(businessSupplies)
            .build());
    entityManager.flush();

    var incomeTotals =
        incomeRepository.sumByActivityBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    assertThat(incomeTotals)
        .hasSize(3)
        .filteredOn(total -> total.getActivityId().equals(rental.getId()))
        .singleElement()
        .satisfies(
            total -> {
              assertThat(total.getActivityId()).isEqualTo(rental.getId());
              assertThat(total.getTotal()).isEqualByComparingTo("1200.00");
            });
    assertThat(incomeTotals)
        .filteredOn(total -> total.getActivityId().equals(teaching.getId()))
        .singleElement()
        .satisfies(total -> assertThat(total.getTotal()).isEqualByComparingTo("2400.10"));
    assertThat(
            expenseRepository.sumByActivityBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        .hasSize(3)
        .filteredOn(total -> total.getActivityId().equals(tutoring.getId()))
        .singleElement()
        .satisfies(total -> assertThat(total.getTotal()).isEqualByComparingTo("75.02"));
    assertThat(
            expenseRepository.sumScheduleEByActivityAndCategoryBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        .singleElement()
        .satisfies(
            total -> {
              assertThat(total.getActivityId()).isEqualTo(rental.getId());
              assertThat(total.getCategoryId()).isEqualTo(repairs.getId());
              assertThat(total.getTotal()).isEqualByComparingTo("250.00");
            });
  }
}
