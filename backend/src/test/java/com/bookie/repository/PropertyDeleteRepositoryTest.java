package com.bookie.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.classification.infrastructure.EmailKeywordPropertyHistoryRepository;
import com.bookie.catalog.classification.infrastructure.PayerPropertyHistoryRepository;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import com.bookie.catalog.property.infrastructure.PropertyRepository;
import com.bookie.model.EmailKeywordPropertyHistory;
import com.bookie.model.EmailType;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Income;
import com.bookie.model.PayerPropertyHistory;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class PropertyDeleteRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private IncomeRepository incomeRepository;
  @Autowired private PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  @Autowired private EmailKeywordPropertyHistoryRepository keywordPropertyHistoryRepo;
  @Autowired private PendingExpenseRepository pendingExpenseRepository;

  private FinancialActivity activity;
  private FinancialCategory expenseCategory;
  private FinancialCategory incomeCategory;

  @BeforeEach
  void setUpClassification() {
    HouseholdMember owner =
        em.persistAndFlush(HouseholdMember.builder().name("Test household").build());
    activity =
        em.persistAndFlush(
            FinancialActivity.builder()
                .name("Needs classification")
                .activityType(ActivityType.OTHER)
                .taxTreatment(TaxTreatment.NONE)
                .owner(owner)
                .build());
    expenseCategory =
        em.persistAndFlush(
            FinancialCategory.builder()
                .key("TEST_EXPENSE")
                .label("Test expense")
                .direction(TransactionDirection.EXPENSE)
                .taxTreatment(TaxTreatment.NONE)
                .build());
    incomeCategory =
        em.persistAndFlush(
            FinancialCategory.builder()
                .key("TEST_INCOME")
                .label("Test income")
                .direction(TransactionDirection.INCOME)
                .taxTreatment(TaxTreatment.NONE)
                .build());
  }

  private Property saveProperty() {
    return em.persistAndFlush(
        Property.builder()
            .name("456 Oak Ave")
            .address("456 Oak Ave, Portland, OR 97201")
            .type(PropertyType.SINGLE_FAMILY)
            .build());
  }

  private Counterparty savePayer() {
    return em.persistAndFlush(
        Counterparty.builder().name("City Water Dept").type(CounterpartyType.COMPANY).build());
  }

  private Expense saveExpenseWithProperty(Property property) {
    return em.persistAndFlush(
        Expense.builder()
            .amount(new BigDecimal("250.00"))
            .description("Property tax")
            .date(LocalDate.of(2024, 4, 1))
            .category(ExpenseCategory.TAXES)
            .financialCategory(expenseCategory)
            .activity(activity)
            .sourceType(ExpenseSource.MANUAL)
            .property(property)
            .build());
  }

  private Income saveIncomeWithProperty(Property property) {
    return em.persistAndFlush(
        Income.builder()
            .amount(new BigDecimal("1800.00"))
            .description("Monthly rent")
            .date(LocalDate.of(2024, 4, 1))
            .source("Tenant A")
            .financialCategory(incomeCategory)
            .activity(activity)
            .sourceType(ExpenseSource.MANUAL)
            .property(property)
            .build());
  }

  @Nested
  class DeleteWithNoRelatedData {

    @Test
    void deleteWithNoRelatedData_succeeds() {
      Property property = saveProperty();
      Long id = property.getId();

      propertyRepository.deleteById(id);

      assertThat(propertyRepository.findById(id)).isEmpty();
    }
  }

  @Nested
  class DeleteWithLinkedExpense {

    @Test
    void deleteWithLinkedExpense_clearPropertyById_thenDeleteSucceeds() {
      Property property = saveProperty();
      Expense expense = saveExpenseWithProperty(property);
      Long propertyId = property.getId();
      Long expenseId = expense.getId();

      expenseRepository.clearPropertyById(propertyId);
      em.flush();
      em.clear();

      assertThatNoException().isThrownBy(() -> propertyRepository.deleteById(propertyId));

      em.flush();
      em.clear();

      Expense reloaded = expenseRepository.findById(expenseId).orElseThrow();
      assertThat(reloaded.getProperty()).isNull();
      assertThat(propertyRepository.findById(propertyId)).isEmpty();
    }
  }

  @Nested
  class DeleteWithLinkedIncome {

    @Test
    void deleteWithLinkedIncome_clearPropertyById_thenDeleteSucceeds() {
      Property property = saveProperty();
      Income income = saveIncomeWithProperty(property);
      Long propertyId = property.getId();
      Long incomeId = income.getId();

      incomeRepository.clearPropertyById(propertyId);
      em.flush();
      em.clear();

      assertThatNoException().isThrownBy(() -> propertyRepository.deleteById(propertyId));

      em.flush();
      em.clear();

      Income reloaded = incomeRepository.findById(incomeId).orElseThrow();
      assertThat(reloaded.getProperty()).isNull();
      assertThat(propertyRepository.findById(propertyId)).isEmpty();
    }
  }

  @Nested
  class DeleteWithPayerPropertyHistory {

    @Test
    void deleteWithPayerPropertyHistory_deleteByPropertyId_thenDeleteSucceeds() {
      Property property = saveProperty();
      Counterparty payer = savePayer();
      Long propertyId = property.getId();

      em.persistAndFlush(
          PayerPropertyHistory.builder().payer(payer).property(property).occurrences(6).build());

      payerPropertyHistoryRepo.deleteByPropertyId(propertyId);
      em.flush();
      em.clear();

      assertThatNoException().isThrownBy(() -> propertyRepository.deleteById(propertyId));

      em.flush();
      assertThat(propertyRepository.findById(propertyId)).isEmpty();
    }
  }

  @Nested
  class DeleteWithKeywordPropertyHistory {

    @Test
    void deleteWithKeywordPropertyHistory_deleteByPropertyId_thenDeleteSucceeds() {
      Property property = saveProperty();
      Long propertyId = property.getId();

      em.persistAndFlush(
          EmailKeywordPropertyHistory.builder()
              .keyword("456-oak-svc")
              .property(property)
              .occurrences(4)
              .build());

      keywordPropertyHistoryRepo.deleteByPropertyId(propertyId);
      em.flush();
      em.clear();

      assertThatNoException().isThrownBy(() -> propertyRepository.deleteById(propertyId));

      em.flush();
      assertThat(propertyRepository.findById(propertyId)).isEmpty();
    }
  }

  @Nested
  class DeleteWithAllRelations {

    @Test
    void deleteWithAllRelations_fullCascadeOrder_succeeds() {
      Property property = saveProperty();
      Counterparty payer = savePayer();
      Long propertyId = property.getId();

      saveExpenseWithProperty(property);
      saveIncomeWithProperty(property);
      em.persistAndFlush(
          PayerPropertyHistory.builder().payer(payer).property(property).occurrences(3).build());
      em.persistAndFlush(
          EmailKeywordPropertyHistory.builder()
              .keyword("oak-meter-9")
              .property(property)
              .occurrences(2)
              .build());

      // Full sequence matching PropertyService.delete()
      expenseRepository.clearPropertyById(propertyId);
      incomeRepository.clearPropertyById(propertyId);
      payerPropertyHistoryRepo.deleteByPropertyId(propertyId);
      keywordPropertyHistoryRepo.deleteByPropertyId(propertyId);
      // Flush bulk-update statements before deleteById so Hibernate's removal tracking
      // operates on a clean first-level cache with no stale state.
      em.flush();
      em.clear();
      propertyRepository.deleteById(propertyId);

      assertThat(propertyRepository.findById(propertyId)).isEmpty();
    }

    @Nested
    class ReclassifyPendingItems {

      @Test
      void preservesPendingItemDirectionWhenRentalActivityIsRemoved() {
        Property property = saveProperty();
        FinancialActivity rental =
            em.persistAndFlush(
                FinancialActivity.builder()
                    .name("456 Oak Ave")
                    .activityType(ActivityType.RENTAL)
                    .taxTreatment(TaxTreatment.SCHEDULE_E)
                    .owner(activity.getOwner())
                    .property(property)
                    .active(true)
                    .build());
        FinancialCategory rentalIncome =
            em.persistAndFlush(
                FinancialCategory.builder()
                    .key("TEST_RENTAL_PENDING_INCOME")
                    .label("Rental income")
                    .direction(TransactionDirection.INCOME)
                    .taxTreatment(TaxTreatment.SCHEDULE_E)
                    .build());
        FinancialCategory rentalExpense =
            em.persistAndFlush(
                FinancialCategory.builder()
                    .key("TEST_RENTAL_PENDING_EXPENSE")
                    .label("Rental expense")
                    .direction(TransactionDirection.EXPENSE)
                    .taxTreatment(TaxTreatment.SCHEDULE_E)
                    .build());
        PendingExpense pendingIncome =
            em.persistAndFlush(
                PendingExpense.builder()
                    .sourceId("pending-income")
                    .emailType(EmailType.INCOME)
                    .status(PendingExpenseStatus.READY)
                    .activity(rental)
                    .financialCategory(rentalIncome)
                    .createdAt(LocalDateTime.now())
                    .build());
        PendingExpense pendingExpense =
            em.persistAndFlush(
                PendingExpense.builder()
                    .sourceId("pending-expense")
                    .emailType(EmailType.EXPENSE)
                    .status(PendingExpenseStatus.READY)
                    .activity(rental)
                    .financialCategory(rentalExpense)
                    .createdAt(LocalDateTime.now())
                    .build());

        pendingExpenseRepository.reassignIncomeClassification(
            rental.getId(), activity, incomeCategory);
        pendingExpenseRepository.reassignExpenseClassification(
            rental.getId(), activity, expenseCategory);
        em.flush();
        em.clear();

        PendingExpense reloadedIncome =
            pendingExpenseRepository.findById(pendingIncome.getId()).orElseThrow();
        PendingExpense reloadedExpense =
            pendingExpenseRepository.findById(pendingExpense.getId()).orElseThrow();
        assertThat(reloadedIncome.getActivity().getId()).isEqualTo(activity.getId());
        assertThat(reloadedIncome.getFinancialCategory().getId()).isEqualTo(incomeCategory.getId());
        assertThat(reloadedExpense.getActivity().getId()).isEqualTo(activity.getId());
        assertThat(reloadedExpense.getFinancialCategory().getId())
            .isEqualTo(expenseCategory.getId());
      }
    }
  }
}
