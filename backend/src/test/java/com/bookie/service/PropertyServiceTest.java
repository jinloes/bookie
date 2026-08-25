package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.ActivityType;
import com.bookie.model.CreatePropertyRequest;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.HouseholdMember;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
import com.bookie.model.UpdatePropertyRequest;
import com.bookie.repository.EmailKeywordPropertyHistoryRepository;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.FinancialActivityRepository;
import com.bookie.repository.FinancialCategoryRepository;
import com.bookie.repository.HouseholdMemberRepository;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.PayerPropertyHistoryRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.bookie.repository.PendingIncomeRepository;
import com.bookie.repository.PropertyRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

  @Mock private PropertyRepository propertyRepository;
  @Mock private ExpenseRepository expenseRepository;
  @Mock private IncomeRepository incomeRepository;
  @Mock private PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  @Mock private EmailKeywordPropertyHistoryRepository keywordPropertyHistoryRepo;
  @Mock private PendingIncomeRepository pendingIncomeRepository;
  @Mock private PendingExpenseRepository pendingExpenseRepository;
  @Mock private FinancialActivityRepository financialActivityRepository;
  @Mock private FinancialCategoryRepository financialCategoryRepository;
  @Mock private HouseholdMemberRepository householdMemberRepository;

  @InjectMocks private PropertyService propertyService;

  private Property property;
  private HouseholdMember owner;

  @BeforeEach
  void setUp() {
    property =
        Property.builder()
            .id(1L)
            .name("123 Main St")
            .address("123 Main St, Springfield, IL")
            .type(PropertyType.SINGLE_FAMILY)
            .notes("Corner lot")
            .build();
    owner = HouseholdMember.builder().id(1L).name("Test household").build();
  }

  @Test
  void findAll_returnsAllProperties() {
    when(propertyRepository.findAll()).thenReturn(List.of(property));

    List<Property> result = propertyService.findAll();

    assertThat(result).hasSize(1).containsExactly(property);
  }

  @Test
  void findById_found_returnsProperty() {
    when(propertyRepository.findById(1L)).thenReturn(Optional.of(property));

    Property result = propertyService.findById(1L);

    assertThat(result).isEqualTo(property);
  }

  @Test
  void findById_notFound_throwsException() {
    when(propertyRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> propertyService.findById(99L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("99");
  }

  @Test
  void save_persistsAndReturnsProperty() {
    when(propertyRepository.save(property)).thenReturn(property);

    Property result = propertyService.save(property);

    assertThat(result).isEqualTo(property);
    verify(propertyRepository).save(property);
  }

  @Nested
  class Create {

    @Test
    void buildsPropertyFromRequestAndSaves() {
      CreatePropertyRequest req =
          new CreatePropertyRequest(
              "123 Main St",
              "123 Main St, Springfield, IL",
              PropertyType.SINGLE_FAMILY,
              "Corner lot",
              Set.of("ACC-001"));
      when(propertyRepository.save(any())).thenReturn(property);
      when(householdMemberRepository.findBySystemKey(HouseholdMemberService.DEFAULT_HOUSEHOLD_KEY))
          .thenReturn(Optional.of(owner));
      when(financialActivityRepository.findAll()).thenReturn(List.of());

      Property result = propertyService.create(req);

      assertThat(result).isEqualTo(property);
      verify(propertyRepository).save(any());
    }

    @Test
    void withNullAccounts_savesWithEmptySet() {
      CreatePropertyRequest req =
          new CreatePropertyRequest("123 Main St", null, PropertyType.SINGLE_FAMILY, null, null);
      when(propertyRepository.save(any())).thenReturn(property);
      when(householdMemberRepository.findBySystemKey(HouseholdMemberService.DEFAULT_HOUSEHOLD_KEY))
          .thenReturn(Optional.of(owner));
      when(financialActivityRepository.findAll()).thenReturn(List.of());

      propertyService.create(req);

      verify(propertyRepository).save(any());
    }
  }

  @Nested
  class Update {

    @Test
    void updatesFieldsFromRequestAndSaves() {
      UpdatePropertyRequest req =
          new UpdatePropertyRequest(
              "456 Oak Ave",
              "456 Oak Ave, Springfield, IL",
              PropertyType.CONDO,
              "Updated notes",
              null);
      when(propertyRepository.findById(1L)).thenReturn(Optional.of(property));
      when(propertyRepository.save(property)).thenReturn(property);

      propertyService.update(1L, req);

      assertThat(property.getName()).isEqualTo("456 Oak Ave");
      assertThat(property.getAddress()).isEqualTo("456 Oak Ave, Springfield, IL");
      assertThat(property.getType()).isEqualTo(PropertyType.CONDO);
      assertThat(property.getNotes()).isEqualTo("Updated notes");
      verify(propertyRepository).save(property);
    }
  }

  @Nested
  class Delete {

    @Test
    void clearsAllRelatedDataBeforeDeletingProperty() {
      propertyService.delete(1L);

      verify(expenseRepository).clearPropertyById(1L);
      verify(incomeRepository).clearPropertyById(1L);
      verify(pendingIncomeRepository).clearPropertyById(1L);
      verify(payerPropertyHistoryRepo).deleteByPropertyId(1L);
      verify(keywordPropertyHistoryRepo).deleteByPropertyId(1L);
      verify(propertyRepository).deleteById(1L);
    }

    @Test
    void reclassifiesTransactionsBeforeDeletingRentalActivity() {
      FinancialActivity rental =
          FinancialActivity.builder()
              .id(10L)
              .name("123 Main St")
              .activityType(ActivityType.RENTAL)
              .taxTreatment(TaxTreatment.SCHEDULE_E)
              .owner(owner)
              .property(property)
              .build();
      FinancialActivity replacement =
          FinancialActivity.builder()
              .id(11L)
              .name("Needs classification")
              .activityType(ActivityType.OTHER)
              .taxTreatment(TaxTreatment.NONE)
              .owner(owner)
              .build();
      FinancialCategory incomeCategory =
          FinancialCategory.builder()
              .id(20L)
              .key("OTHER_INCOME")
              .direction(TransactionDirection.INCOME)
              .taxTreatment(TaxTreatment.NONE)
              .build();
      FinancialCategory expenseCategory =
          FinancialCategory.builder()
              .id(21L)
              .key("OTHER_EXPENSE")
              .direction(TransactionDirection.EXPENSE)
              .taxTreatment(TaxTreatment.NONE)
              .build();
      when(financialActivityRepository.findByPropertyId(1L)).thenReturn(Optional.of(rental));
      when(financialActivityRepository.findBySystemKey("NEEDS_CLASSIFICATION"))
          .thenReturn(Optional.of(replacement));
      when(financialCategoryRepository.findByKey("OTHER_INCOME"))
          .thenReturn(Optional.of(incomeCategory));
      when(financialCategoryRepository.findByKey("OTHER_EXPENSE"))
          .thenReturn(Optional.of(expenseCategory));

      propertyService.delete(1L);

      verify(expenseRepository).reassignClassification(10L, replacement, expenseCategory);
      verify(incomeRepository).reassignClassification(10L, replacement, incomeCategory);
      verify(pendingIncomeRepository).reassignClassification(10L, replacement, incomeCategory);
      verify(pendingExpenseRepository)
          .reassignIncomeClassification(10L, replacement, incomeCategory);
      verify(pendingExpenseRepository)
          .reassignExpenseClassification(10L, replacement, expenseCategory);
      verify(financialActivityRepository).delete(rental);
    }
  }
}
