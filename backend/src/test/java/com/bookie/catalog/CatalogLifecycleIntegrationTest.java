package com.bookie.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.activity.infrastructure.FinancialActivityRepository;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.application.UpsertCounterpartyCommand;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.catalog.financialcategory.infrastructure.FinancialCategoryRepository;
import com.bookie.catalog.household.application.HouseholdCatalog;
import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.household.infrastructure.HouseholdMemberRepository;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import com.bookie.catalog.property.infrastructure.PropertyRepository;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.ledger.domain.ExpenseSource;
import com.bookie.ledger.domain.FinancialTransaction;
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
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private FinancialActivityRepository activityRepository;
  @Autowired private HouseholdMemberRepository householdMemberRepository;
  @Autowired private FinancialCategoryRepository categoryRepository;
  @Autowired private InboxItemStore inboxItemStore;
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

    FinancialTransaction expense =
        transaction(TransactionDirection.EXPENSE, "142.67", "Repair materials", rental, null);
    FinancialTransaction income =
        transaction(TransactionDirection.INCOME, "1875.25", "June rent", rental, null);
    InboxItem pendingIncome =
        inboxItem(
            "lifecycle-pending-income",
            TransactionDirection.INCOME,
            rental,
            rentalIncome,
            property.getId(),
            null);
    InboxItem pendingExpense =
        inboxItem(
            "lifecycle-pending-expense",
            TransactionDirection.EXPENSE,
            rental,
            rentalExpense,
            null,
            null);
    Long propertyId = property.getId();
    Long rentalId = rental.getId();
    long transactionCount = count("SELECT COUNT(*) FROM financial_transactions");
    long inboxCount = count("SELECT COUNT(*) FROM inbox_items");
    entityManager.flush();
    entityManager.clear();

    propertyCatalog.delete(propertyId);

    entityManager.flush();
    entityManager.clear();
    FinancialTransaction reloadedExpense =
        entityManager.find(FinancialTransaction.class, expense.getId());
    FinancialTransaction reloadedIncome =
        entityManager.find(FinancialTransaction.class, income.getId());
    InboxItem reloadedPendingIncome = inboxItemStore.findById(pendingIncome.getId()).orElseThrow();
    InboxItem reloadedPendingExpense =
        inboxItemStore.findById(pendingExpense.getId()).orElseThrow();

    assertThat(propertyRepository.findById(propertyId)).isEmpty();
    assertThat(activityRepository.findById(rentalId)).isEmpty();
    assertThat(reloadedExpense.getActivity().getId()).isEqualTo(fallback.getId());
    assertThat(reloadedIncome.getActivity().getId()).isEqualTo(fallback.getId());
    assertThat(reloadedExpense.getNeutralCategory().getId())
        .isEqualTo(neutralCategoryFor(fallbackExpense).getId());
    assertThat(reloadedIncome.getNeutralCategory().getId())
        .isEqualTo(neutralCategoryFor(fallbackIncome).getId());
    assertThat(reloadedPendingIncome.getLegacyPropertyId()).isNull();
    assertThat(reloadedPendingIncome.getLegacyActivityId()).isEqualTo(fallback.getId());
    assertThat(reloadedPendingExpense.getLegacyActivityId()).isEqualTo(fallback.getId());
    assertThat(reloadedPendingIncome.getLegacyCategoryId()).isEqualTo(fallbackIncome.getId());
    assertThat(reloadedPendingExpense.getLegacyCategoryId()).isEqualTo(fallbackExpense.getId());
    assertThat(reloadedPendingExpense.getConfiguredActivityId()).isNull();
    assertThat(count("SELECT COUNT(*) FROM financial_transactions")).isEqualTo(transactionCount);
    assertThat(count("SELECT COUNT(*) FROM inbox_items")).isEqualTo(inboxCount);
  }

  @Test
  void deletingCounterpartyDetachesReferencesAndPreservesEveryFinancialRecord() {
    HouseholdMember owner = defaultOwner();
    FinancialActivity fallback = fallbackActivity(owner);
    FinancialCategory incomeCategory =
        category("OTHER_INCOME", TransactionDirection.INCOME, TaxTreatment.NONE);
    Counterparty counterparty =
        counterpartyCatalog.create(
            new UpsertCounterpartyCommand(
                "Lifecycle Counterparty",
                CounterpartyType.COMPANY,
                List.of("Lifecycle Alias"),
                Set.of("account-001")));
    Long counterpartyId = counterparty.getId();

    FinancialTransaction expense =
        transaction(
            TransactionDirection.EXPENSE, "42.00", "Utility expense", fallback, counterpartyId);
    FinancialTransaction income =
        transaction(
            TransactionDirection.INCOME, "75.00", "Reimbursement", fallback, counterpartyId);
    InboxItem pendingIncome =
        inboxItem(
            "counterparty-pending-income",
            TransactionDirection.INCOME,
            fallback,
            incomeCategory,
            null,
            counterpartyId);
    long transactionCount = count("SELECT COUNT(*) FROM financial_transactions");
    entityManager.flush();
    entityManager.clear();

    counterpartyCatalog.delete(counterpartyId);

    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(FinancialTransaction.class, expense.getId()).getCounterpartyId())
        .isNull();
    assertThat(entityManager.find(FinancialTransaction.class, income.getId()).getCounterpartyId())
        .isNull();
    assertThat(
            inboxItemStore.findById(pendingIncome.getId()).orElseThrow().getLegacyCounterpartyId())
        .isNull();
    assertThat(counterpartyCatalog.findOptionalById(counterpartyId)).isEmpty();
    assertThat(count("SELECT COUNT(*) FROM counterparties WHERE id = " + counterpartyId)).isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM counterparty_aliases WHERE counterparty_id = "
                    + counterpartyId))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM counterparty_accounts WHERE counterparty_id = "
                    + counterpartyId))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM financial_transactions")).isEqualTo(transactionCount);
  }

  private FinancialTransaction transaction(
      TransactionDirection direction,
      String amount,
      String description,
      FinancialActivity activity,
      Long counterpartyId) {
    FinancialTransaction transaction =
        FinancialTransaction.builder()
            .amount(new BigDecimal(amount))
            .direction(direction)
            .date(LocalDate.of(2026, 6, 2))
            .description(description)
            .activity(activity)
            .neutralCategory(anyNeutralCategory(direction))
            .counterpartyId(counterpartyId)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    entityManager.persist(transaction);
    entityManager.flush();
    return transaction;
  }

  private InboxItem inboxItem(
      String sourceId,
      TransactionDirection direction,
      FinancialActivity activity,
      FinancialCategory category,
      Long propertyId,
      Long counterpartyId) {
    return inboxItemStore.save(
        InboxItem.builder()
            .origin(ExpenseSource.RECEIPT)
            .legacySourceId(sourceId)
            .state(InboxState.READY)
            .rawStatus("READY")
            .proposedDirection(direction)
            .legacyActivityId(activity.getId())
            .configuredActivityId(activity.getId())
            .legacyCategoryId(category.getId())
            .legacyPropertyId(propertyId)
            .legacyCounterpartyId(counterpartyId)
            .build());
  }

  private NeutralCategory anyNeutralCategory(TransactionDirection direction) {
    return neutralCategory("LIFECYCLE_" + direction.name(), direction);
  }

  private NeutralCategory neutralCategory(String key, TransactionDirection direction) {
    return entityManager
        .createQuery(
            "SELECT category FROM NeutralCategory category WHERE category.key = :key",
            NeutralCategory.class)
        .setParameter("key", key)
        .getResultStream()
        .findFirst()
        .orElseGet(
            () -> {
              NeutralCategory created =
                  NeutralCategory.builder().key(key).label(key).direction(direction).build();
              entityManager.persist(created);
              return created;
            });
  }

  private NeutralCategory neutralCategoryFor(FinancialCategory category) {
    return entityManager.find(LegacyCategoryMap.class, category.getId()).getNeutralCategory();
  }

  private long count(String sql) {
    return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
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
    FinancialCategory category =
        categoryRepository
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
    if (entityManager.find(LegacyCategoryMap.class, category.getId()) == null) {
      entityManager.persist(
          LegacyCategoryMap.builder()
              .legacyCategoryId(category.getId())
              .legacyCategoryKey(key)
              .neutralCategory(neutralCategory("LIFECYCLE_MAP_" + key, direction))
              .build());
      entityManager.flush();
    }
    return category;
  }
}
