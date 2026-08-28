package com.bookie.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.activity.infrastructure.FinancialActivityRepository;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.application.UpsertCounterpartyCommand;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.household.application.HouseholdCatalog;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.household.infrastructure.HouseholdMemberRepository;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import com.bookie.catalog.property.infrastructure.PropertyRepository;
import com.bookie.model.EmailType;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Income;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.PendingIncome;
import com.bookie.model.PendingIncomeStatus;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.FinancialCategoryRepository;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.bookie.repository.PendingIncomeRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CatalogLifecycleIntegrationTest {

  @Autowired private PropertyCatalog propertyCatalog;
  @Autowired private CounterpartyCatalog counterpartyCatalog;
  @Autowired private ActivityCatalog activityCatalog;
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private FinancialActivityRepository activityRepository;
  @Autowired private HouseholdMemberRepository householdMemberRepository;
  @Autowired private FinancialCategoryRepository categoryRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private IncomeRepository incomeRepository;
  @Autowired private PendingIncomeRepository pendingIncomeRepository;
  @Autowired private PendingExpenseRepository pendingExpenseRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void deletingPropertyReclassifiesReferencesAndPreservesEveryFinancialRecord() {
    HouseholdMember owner = defaultOwner();
    FinancialActivity fallback = fallbackActivity(owner);
    FinancialCategory fallbackExpense =
        category("OTHER_EXPENSE", TransactionDirection.EXPENSE, TaxTreatment.NONE);
    FinancialCategory fallbackIncome =
        category("OTHER_INCOME", TransactionDirection.INCOME, TaxTreatment.NONE);
    FinancialCategory rentalExpense =
        category("TEST_RENTAL_EXPENSE", TransactionDirection.EXPENSE, TaxTreatment.SCHEDULE_E);
    FinancialCategory rentalIncome =
        category("TEST_RENTAL_INCOME", TransactionDirection.INCOME, TaxTreatment.SCHEDULE_E);
    Property property =
        propertyRepository.saveAndFlush(
            Property.builder()
                .name("Lifecycle Rental")
                .address("10 Lifecycle Lane")
                .type(PropertyType.SINGLE_FAMILY)
                .build());
    FinancialActivity rental =
        activityRepository.saveAndFlush(
            FinancialActivity.builder()
                .name("Lifecycle Rental")
                .activityType(ActivityType.RENTAL)
                .taxTreatment(TaxTreatment.SCHEDULE_E)
                .owner(owner)
                .property(property)
                .active(true)
                .build());

    Expense expense =
        expenseRepository.saveAndFlush(
            Expense.builder()
                .amount(new BigDecimal("142.67"))
                .description("Repair materials")
                .date(LocalDate.of(2026, 6, 2))
                .category(ExpenseCategory.REPAIRS)
                .sourceType(ExpenseSource.MANUAL)
                .property(property)
                .activity(rental)
                .financialCategory(rentalExpense)
                .build());
    Income income =
        incomeRepository.saveAndFlush(
            Income.builder()
                .amount(new BigDecimal("1875.25"))
                .description("June rent")
                .date(LocalDate.of(2026, 6, 1))
                .sourceType(ExpenseSource.MANUAL)
                .property(property)
                .activity(rental)
                .financialCategory(rentalIncome)
                .build());
    PendingIncome pendingIncome =
        pendingIncomeRepository.saveAndFlush(
            PendingIncome.builder()
                .sourceId("lifecycle-pending-income")
                .status(PendingIncomeStatus.READY)
                .amount(new BigDecimal("900.00"))
                .description("Partial rent")
                .date(LocalDate.of(2026, 6, 3))
                .createdAt(LocalDateTime.now())
                .property(property)
                .activity(rental)
                .financialCategory(rentalIncome)
                .build());
    PendingExpense pendingExpense =
        pendingExpenseRepository.saveAndFlush(
            PendingExpense.builder()
                .sourceId("lifecycle-pending-expense")
                .status(PendingExpenseStatus.READY)
                .emailType(EmailType.EXPENSE)
                .createdAt(LocalDateTime.now())
                .activity(rental)
                .financialCategory(rentalExpense)
                .build());
    PendingExpense pendingIncomeInLegacyQueue =
        pendingExpenseRepository.saveAndFlush(
            PendingExpense.builder()
                .sourceId("lifecycle-pending-income-legacy")
                .status(PendingExpenseStatus.READY)
                .emailType(EmailType.INCOME)
                .createdAt(LocalDateTime.now())
                .activity(rental)
                .financialCategory(rentalIncome)
                .build());
    Long propertyId = property.getId();
    Long rentalId = rental.getId();
    entityManager.flush();
    entityManager.clear();

    propertyCatalog.delete(propertyId);

    entityManager.flush();
    entityManager.clear();
    Expense reloadedExpense = expenseRepository.findById(expense.getId()).orElseThrow();
    Income reloadedIncome = incomeRepository.findById(income.getId()).orElseThrow();
    PendingIncome reloadedPendingIncome =
        pendingIncomeRepository.findById(pendingIncome.getId()).orElseThrow();
    PendingExpense reloadedPendingExpense =
        pendingExpenseRepository.findById(pendingExpense.getId()).orElseThrow();
    PendingExpense reloadedPendingLegacyIncome =
        pendingExpenseRepository.findById(pendingIncomeInLegacyQueue.getId()).orElseThrow();

    assertThat(propertyRepository.findById(propertyId)).isEmpty();
    assertThat(activityRepository.findById(rentalId)).isEmpty();
    assertThat(reloadedExpense.getProperty()).isNull();
    assertThat(reloadedIncome.getProperty()).isNull();
    assertThat(reloadedPendingIncome.getProperty()).isNull();
    assertThat(reloadedExpense.getActivity().getId()).isEqualTo(fallback.getId());
    assertThat(reloadedIncome.getActivity().getId()).isEqualTo(fallback.getId());
    assertThat(reloadedPendingIncome.getActivity().getId()).isEqualTo(fallback.getId());
    assertThat(reloadedPendingExpense.getActivity().getId()).isEqualTo(fallback.getId());
    assertThat(reloadedPendingLegacyIncome.getActivity().getId()).isEqualTo(fallback.getId());
    assertThat(reloadedExpense.getFinancialCategory().getId()).isEqualTo(fallbackExpense.getId());
    assertThat(reloadedIncome.getFinancialCategory().getId()).isEqualTo(fallbackIncome.getId());
    assertThat(reloadedPendingExpense.getFinancialCategory().getId())
        .isEqualTo(fallbackExpense.getId());
    assertThat(reloadedPendingLegacyIncome.getFinancialCategory().getId())
        .isEqualTo(fallbackIncome.getId());
    assertThat(expenseRepository.count()).isPositive();
    assertThat(incomeRepository.count()).isPositive();
    assertThat(pendingIncomeRepository.count()).isPositive();
    assertThat(pendingExpenseRepository.count()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void deletingCounterpartyDetachesReferencesAndPreservesEveryFinancialRecord() {
    HouseholdMember owner = defaultOwner();
    FinancialActivity fallback = fallbackActivity(owner);
    FinancialCategory expenseCategory =
        category("OTHER_EXPENSE", TransactionDirection.EXPENSE, TaxTreatment.NONE);
    FinancialCategory incomeCategory =
        category("OTHER_INCOME", TransactionDirection.INCOME, TaxTreatment.NONE);
    Counterparty counterparty =
        counterpartyCatalog.create(
            new UpsertCounterpartyCommand(
                "Lifecycle Counterparty",
                CounterpartyType.COMPANY,
                List.of("Lifecycle Alias"),
                Set.of("account-001")));

    Expense expense =
        expenseRepository.saveAndFlush(
            Expense.builder()
                .amount(new BigDecimal("42.00"))
                .description("Utility expense")
                .date(LocalDate.of(2026, 7, 1))
                .category(ExpenseCategory.UTILITIES)
                .sourceType(ExpenseSource.MANUAL)
                .payer(counterparty)
                .activity(fallback)
                .financialCategory(expenseCategory)
                .build());
    Income income =
        incomeRepository.saveAndFlush(
            Income.builder()
                .amount(new BigDecimal("75.00"))
                .description("Reimbursement")
                .date(LocalDate.of(2026, 7, 2))
                .sourceType(ExpenseSource.MANUAL)
                .payer(counterparty)
                .activity(fallback)
                .financialCategory(incomeCategory)
                .build());
    PendingIncome pendingIncome =
        pendingIncomeRepository.saveAndFlush(
            PendingIncome.builder()
                .sourceId("counterparty-pending-income")
                .status(PendingIncomeStatus.READY)
                .createdAt(LocalDateTime.now())
                .payer(counterparty)
                .activity(fallback)
                .financialCategory(incomeCategory)
                .build());
    Long counterpartyId = counterparty.getId();
    entityManager.flush();
    entityManager.clear();

    counterpartyCatalog.delete(counterpartyId);

    entityManager.flush();
    entityManager.clear();
    assertThat(expenseRepository.findById(expense.getId()).orElseThrow().getPayer()).isNull();
    assertThat(incomeRepository.findById(income.getId()).orElseThrow().getPayer()).isNull();
    assertThat(pendingIncomeRepository.findById(pendingIncome.getId()).orElseThrow().getPayer())
        .isNull();
    assertThat(counterpartyCatalog.findOptionalById(counterpartyId)).isEmpty();
    assertThat(
            ((Number)
                    entityManager
                        .createNativeQuery(
                            "SELECT COUNT(*) FROM counterparties WHERE id = :counterpartyId")
                        .setParameter("counterpartyId", counterpartyId)
                        .getSingleResult())
                .longValue())
        .isZero();
    assertThat(
            ((Number)
                    entityManager
                        .createNativeQuery(
                            "SELECT COUNT(*) FROM legacy_payer_map WHERE payer_id = :payerId")
                        .setParameter("payerId", counterpartyId)
                        .getSingleResult())
                .longValue())
        .isZero();
    assertThat(expenseRepository.count()).isPositive();
    assertThat(incomeRepository.count()).isPositive();
    assertThat(pendingIncomeRepository.count()).isPositive();
  }

  private HouseholdMember defaultOwner() {
    return householdMemberRepository
        .findBySystemKey(HouseholdCatalog.DEFAULT_HOUSEHOLD_KEY)
        .orElseGet(
            () ->
                householdMemberRepository.saveAndFlush(
                    HouseholdMember.builder()
                        .name("Lifecycle Household")
                        .active(true)
                        .systemKey(HouseholdCatalog.DEFAULT_HOUSEHOLD_KEY)
                        .build()));
  }

  private FinancialActivity fallbackActivity(HouseholdMember owner) {
    return activityRepository
        .findBySystemKey(ActivityCatalog.NEEDS_CLASSIFICATION_KEY)
        .orElseGet(
            () ->
                activityRepository.saveAndFlush(
                    FinancialActivity.builder()
                        .name("Needs classification")
                        .activityType(ActivityType.OTHER)
                        .taxTreatment(TaxTreatment.NONE)
                        .owner(owner)
                        .active(true)
                        .systemKey(ActivityCatalog.NEEDS_CLASSIFICATION_KEY)
                        .build()));
  }

  private FinancialCategory category(
      String key, TransactionDirection direction, TaxTreatment taxTreatment) {
    return categoryRepository
        .findByKey(key)
        .orElseGet(
            () ->
                categoryRepository.saveAndFlush(
                    FinancialCategory.builder()
                        .key(key)
                        .label(key)
                        .direction(direction)
                        .taxTreatment(taxTreatment)
                        .active(true)
                        .system(true)
                        .build()));
  }
}
