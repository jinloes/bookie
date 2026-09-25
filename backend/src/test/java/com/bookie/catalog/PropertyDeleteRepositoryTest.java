package com.bookie.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.classification.domain.EmailKeywordPropertyHistory;
import com.bookie.catalog.classification.domain.PayerPropertyHistory;
import com.bookie.catalog.classification.infrastructure.EmailKeywordPropertyHistoryRepository;
import com.bookie.catalog.classification.infrastructure.PayerPropertyHistoryRepository;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import com.bookie.catalog.property.infrastructure.PropertyRepository;
import com.bookie.config.ClockConfiguration;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

// Include the JPA inbox store adapter behind its public port.
@DataJpaTest(
    includeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InboxItemStore.class))
@Import(ClockConfiguration.class)
class PropertyDeleteRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  @Autowired private EmailKeywordPropertyHistoryRepository keywordPropertyHistoryRepo;
  @Autowired private InboxItemStore inboxItemStore;

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

  private InboxItem saveInboxItem(
      TransactionDirection direction, Long activityId, Long categoryId, Long propertyId) {
    return em.persistAndFlush(
        InboxItem.builder()
            .origin(ExpenseSource.RECEIPT)
            .state(InboxState.READY)
            .rawStatus("READY")
            .proposedDirection(direction)
            .legacyActivityId(activityId)
            .configuredActivityId(activityId)
            .legacyCategoryId(categoryId)
            .legacyPropertyId(propertyId)
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
  class DeleteWithLinkedInboxItem {

    @Test
    void detachPropertyClearsInboxReferencesWithoutDeletingTheItem() {
      Property property = saveProperty();
      Long propertyId = property.getId();
      InboxItem item =
          saveInboxItem(
              TransactionDirection.INCOME, activity.getId(), incomeCategory.getId(), propertyId);

      assertThat(inboxItemStore.countPropertyReferences(propertyId)).isOne();
      inboxItemStore.detachProperty(propertyId);
      em.flush();
      em.clear();

      assertThat(inboxItemStore.countPropertyReferences(propertyId)).isZero();
      assertThatNoException().isThrownBy(() -> propertyRepository.deleteById(propertyId));
      em.flush();
      InboxItem reloaded = em.find(InboxItem.class, item.getId());
      assertThat(reloaded.getLegacyPropertyId()).isNull();
      assertThat(reloaded.getLegacyActivityId()).isEqualTo(activity.getId());
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

      saveInboxItem(
          TransactionDirection.EXPENSE, activity.getId(), expenseCategory.getId(), propertyId);
      em.persistAndFlush(
          PayerPropertyHistory.builder().payer(payer).property(property).occurrences(3).build());
      em.persistAndFlush(
          EmailKeywordPropertyHistory.builder()
              .keyword("oak-meter-9")
              .property(property)
              .occurrences(2)
              .build());

      // Full sequence matching PropertyService.delete()
      inboxItemStore.detachProperty(propertyId);
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
    class ReclassifyInboxItems {

      @Test
      void preservesInboxItemDirectionWhenRentalActivityIsRemoved() {
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
        InboxItem income =
            saveInboxItem(TransactionDirection.INCOME, rental.getId(), rentalIncome.getId(), null);
        InboxItem expense =
            saveInboxItem(
                TransactionDirection.EXPENSE, rental.getId(), rentalExpense.getId(), null);
        InboxItem undecided = saveInboxItem(null, rental.getId(), rentalExpense.getId(), null);
        InboxItem unrelated =
            saveInboxItem(
                TransactionDirection.EXPENSE, activity.getId(), expenseCategory.getId(), null);
        assertThat(inboxItemStore.countActivityReferences(rental.getId())).isEqualTo(3);

        inboxItemStore.reassignActivity(
            rental.getId(), activity.getId(), incomeCategory.getId(), expenseCategory.getId());
        em.flush();
        em.clear();

        assertThat(inboxItemStore.countActivityReferences(rental.getId())).isZero();
        InboxItem reloadedIncome = em.find(InboxItem.class, income.getId());
        assertThat(reloadedIncome.getLegacyActivityId()).isEqualTo(activity.getId());
        assertThat(reloadedIncome.getLegacyCategoryId()).isEqualTo(incomeCategory.getId());
        assertThat(reloadedIncome.getConfiguredActivityId()).isNull();
        InboxItem reloadedExpense = em.find(InboxItem.class, expense.getId());
        assertThat(reloadedExpense.getLegacyActivityId()).isEqualTo(activity.getId());
        assertThat(reloadedExpense.getLegacyCategoryId()).isEqualTo(expenseCategory.getId());
        assertThat(em.find(InboxItem.class, undecided.getId()).getLegacyCategoryId())
            .isEqualTo(expenseCategory.getId());
        InboxItem reloadedUnrelated = em.find(InboxItem.class, unrelated.getId());
        assertThat(reloadedUnrelated.getConfiguredActivityId()).isEqualTo(activity.getId());
      }
    }
  }
}
