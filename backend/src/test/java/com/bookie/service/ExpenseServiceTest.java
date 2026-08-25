package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.bookie.model.CreateExpenseRequest;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Payer;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
import com.bookie.model.UpdateExpenseRequest;
import com.bookie.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

  @Mock private ExpenseRepository expenseRepository;
  @Mock private PropertyHistoryService propertyHistoryService;
  @Mock private PropertyService propertyService;
  @Mock private PayerService payerService;
  @Mock private ReceiptService receiptService;
  @Mock private FinancialActivityService financialActivityService;
  @Mock private FinancialCategoryService financialCategoryService;

  @InjectMocks private ExpenseService expenseService;

  private Expense expense;
  private Property property;

  @BeforeEach
  void setUp() {
    property =
        Property.builder()
            .id(1L)
            .name("123 Main St")
            .address("123 Main St")
            .type(PropertyType.SINGLE_FAMILY)
            .build();
    expense =
        Expense.builder()
            .id(1L)
            .amount(new BigDecimal("500.00"))
            .description("Roof repair")
            .date(LocalDate.of(2024, 1, 15))
            .category(ExpenseCategory.REPAIRS)
            .property(property)
            .build();
    lenient()
        .when(financialActivityService.resolveForTransaction(any(), any()))
        .thenAnswer(
            invocation -> {
              Long propertyId = invocation.getArgument(1);
              return FinancialActivity.builder()
                  .id(propertyId == null ? 99L : 10L)
                  .name(propertyId == null ? "Needs classification" : "123 Main St")
                  .taxTreatment(propertyId == null ? TaxTreatment.NONE : TaxTreatment.SCHEDULE_E)
                  .property(propertyId == null ? null : property)
                  .active(true)
                  .build();
            });
    lenient()
        .when(
            financialCategoryService.resolve(any(), any(), eq(TransactionDirection.EXPENSE), any()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(1);
              return FinancialCategory.builder()
                  .id(20L)
                  .key(key == null ? "OTHER_EXPENSE" : key)
                  .label("Category")
                  .direction(TransactionDirection.EXPENSE)
                  .taxTreatment(TaxTreatment.SCHEDULE_E)
                  .active(true)
                  .build();
            });
    lenient()
        .when(financialCategoryService.toLegacyExpenseCategory(any()))
        .thenAnswer(
            invocation -> {
              String key = ((FinancialCategory) invocation.getArgument(0)).getKey();
              try {
                return ExpenseCategory.valueOf(key);
              } catch (IllegalArgumentException ignored) {
                return ExpenseCategory.OTHER;
              }
            });
  }

  @Test
  void findAll_returnsAllExpenses() {
    when(expenseRepository.findAll(Sort.by(Sort.Direction.DESC, "date")))
        .thenReturn(List.of(expense));

    List<Expense> result = expenseService.findAll();

    assertThat(result).hasSize(1).containsExactly(expense);
  }

  @Test
  void findById_found_returnsExpense() {
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));

    Expense result = expenseService.findById(1L);

    assertThat(result).isEqualTo(expense);
  }

  @Test
  void findById_notFound_throwsException() {
    when(expenseRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> expenseService.findById(99L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("99");
  }

  @Test
  void save_persistsAndReturnsExpense() {
    when(expenseRepository.save(expense)).thenReturn(expense);

    Expense result = expenseService.save(expense);

    assertThat(result).isEqualTo(expense);
    verify(expenseRepository).save(expense);
  }

  @Nested
  class Create {

    @Test
    void create_resolvesPropertyAndPayerAndSaves() {
      Payer payer = Payer.builder().id(2L).name("John").build();
      when(payerService.findById(2L)).thenReturn(payer);
      when(expenseRepository.save(any())).thenReturn(expense);

      CreateExpenseRequest req =
          new CreateExpenseRequest(
              new BigDecimal("500.00"),
              "Roof repair",
              LocalDate.of(2024, 1, 15),
              ExpenseCategory.REPAIRS,
              1L,
              2L,
              null,
              null,
              null);

      Expense result = expenseService.create(req);

      assertThat(result).isEqualTo(expense);
      ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
      verify(expenseRepository).save(captor.capture());
      assertThat(captor.getValue().getProperty()).isEqualTo(property);
      assertThat(captor.getValue().getPayer()).isEqualTo(payer);
    }

    @Test
    void create_withNullPropertyAndPayer_savesWithoutLookup() {
      when(expenseRepository.save(any())).thenReturn(expense);

      CreateExpenseRequest req =
          new CreateExpenseRequest(
              new BigDecimal("500.00"),
              "Roof repair",
              LocalDate.of(2024, 1, 15),
              ExpenseCategory.REPAIRS,
              null,
              null,
              null,
              null,
              null);

      expenseService.create(req);

      verify(propertyService, never()).findById(any());
      verify(payerService, never()).findById(any());
    }

    @Test
    void create_withReceipt_movesReceiptToYearFolder() {
      Expense withReceipt =
          Expense.builder()
              .id(2L)
              .amount(new BigDecimal("414.00"))
              .description("HOA Fee")
              .date(LocalDate.of(2024, 5, 1))
              .category(ExpenseCategory.OTHER)
              .sourceType(ExpenseSource.RECEIPT)
              .receiptOneDriveId("item-abc")
              .build();
      when(expenseRepository.save(any())).thenReturn(withReceipt);

      CreateExpenseRequest req =
          new CreateExpenseRequest(
              new BigDecimal("414.00"),
              "HOA Fee",
              LocalDate.of(2024, 5, 1),
              ExpenseCategory.OTHER,
              null,
              null,
              "item-abc",
              "hoa.pdf",
              ExpenseSource.RECEIPT);

      expenseService.create(req);

      verify(receiptService).moveTaxesFolder("item-abc", 2024);
    }

    @Test
    void create_withoutReceipt_doesNotMoveFolder() {
      when(expenseRepository.save(any())).thenReturn(expense);

      CreateExpenseRequest req =
          new CreateExpenseRequest(
              new BigDecimal("500.00"),
              "Roof repair",
              LocalDate.of(2024, 1, 15),
              ExpenseCategory.REPAIRS,
              null,
              null,
              null,
              null,
              null);

      expenseService.create(req);

      verify(receiptService, never()).moveTaxesFolder(any(), anyInt());
    }

    @Test
    void educatorExpensePersistsW2ActivityAndEducatorCategory() {
      FinancialActivity teaching =
          FinancialActivity.builder()
              .id(10L)
              .name("Teaching")
              .taxTreatment(TaxTreatment.W2)
              .active(true)
              .build();
      FinancialCategory educatorExpense =
          FinancialCategory.builder()
              .id(30L)
              .key("EDUCATOR_EXPENSE")
              .direction(TransactionDirection.EXPENSE)
              .taxTreatment(TaxTreatment.W2)
              .active(true)
              .build();
      when(financialActivityService.resolveForTransaction(10L, null)).thenReturn(teaching);
      when(financialCategoryService.resolve(30L, null, TransactionDirection.EXPENSE, teaching))
          .thenReturn(educatorExpense);
      when(financialCategoryService.toLegacyExpenseCategory(educatorExpense))
          .thenReturn(ExpenseCategory.OTHER);
      when(expenseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Expense saved =
          expenseService.create(
              new CreateExpenseRequest(
                  new BigDecimal("125.00"),
                  "Classroom supplies",
                  LocalDate.of(2026, 8, 20),
                  null,
                  null,
                  null,
                  null,
                  null,
                  ExpenseSource.MANUAL,
                  10L,
                  30L));

      assertThat(saved.getActivity()).isEqualTo(teaching);
      assertThat(saved.getFinancialCategory()).isEqualTo(educatorExpense);
    }

    @Test
    void scheduleCExpensePersistsBusinessActivityAndCategory() {
      FinancialActivity tutoring =
          FinancialActivity.builder()
              .id(11L)
              .name("Tutoring")
              .taxTreatment(TaxTreatment.SCHEDULE_C)
              .active(true)
              .build();
      FinancialCategory supplies =
          FinancialCategory.builder()
              .id(31L)
              .key("SCHEDULE_C_SUPPLIES")
              .direction(TransactionDirection.EXPENSE)
              .taxTreatment(TaxTreatment.SCHEDULE_C)
              .active(true)
              .build();
      when(financialActivityService.resolveForTransaction(11L, null)).thenReturn(tutoring);
      when(financialCategoryService.resolve(31L, null, TransactionDirection.EXPENSE, tutoring))
          .thenReturn(supplies);
      when(financialCategoryService.toLegacyExpenseCategory(supplies))
          .thenReturn(ExpenseCategory.SUPPLIES);
      when(expenseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Expense saved =
          expenseService.create(
              new CreateExpenseRequest(
                  new BigDecimal("75.00"),
                  "Tutoring materials",
                  LocalDate.of(2026, 9, 1),
                  null,
                  null,
                  null,
                  null,
                  null,
                  ExpenseSource.MANUAL,
                  11L,
                  31L));

      assertThat(saved.getActivity()).isEqualTo(tutoring);
      assertThat(saved.getFinancialCategory()).isEqualTo(supplies);
    }
  }

  @Nested
  class UpdateWithRequest {

    @Test
    void update_resolvesPropertyAndPayerAndUpdatesExpense() {
      Payer payer = Payer.builder().id(2L).name("John").build();
      when(payerService.findById(2L)).thenReturn(payer);
      when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
      when(expenseRepository.save(expense)).thenReturn(expense);

      UpdateExpenseRequest req =
          new UpdateExpenseRequest(
              new BigDecimal("750.00"),
              "Updated repair",
              LocalDate.of(2024, 2, 1),
              ExpenseCategory.CLEANING_AND_MAINTENANCE,
              1L,
              2L,
              null,
              null);

      Expense result = expenseService.update(1L, req);

      assertThat(result.getAmount()).isEqualByComparingTo("750.00");
      assertThat(result.getProperty()).isEqualTo(property);
      assertThat(result.getPayer()).isEqualTo(payer);
    }

    @Test
    void update_withNullPropertyAndPayer_updatesWithoutLookup() {
      when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
      when(expenseRepository.save(expense)).thenReturn(expense);

      UpdateExpenseRequest req =
          new UpdateExpenseRequest(
              new BigDecimal("750.00"),
              "Updated repair",
              LocalDate.of(2024, 2, 1),
              ExpenseCategory.CLEANING_AND_MAINTENANCE,
              null,
              null,
              null,
              null);

      expenseService.update(1L, req);

      verify(propertyService, never()).findById(any());
      verify(payerService, never()).findById(any());
    }
  }

  @Test
  void update_updatesFieldsAndSaves() {
    Property otherProperty =
        Property.builder()
            .id(2L)
            .name("456 Oak Ave")
            .address("456 Oak Ave")
            .type(PropertyType.SINGLE_FAMILY)
            .build();
    Expense updated =
        Expense.builder()
            .amount(new BigDecimal("750.00"))
            .description("Updated repair")
            .date(LocalDate.of(2024, 2, 1))
            .category(ExpenseCategory.CLEANING_AND_MAINTENANCE)
            .property(otherProperty)
            .build();
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    when(expenseRepository.save(expense)).thenReturn(expense);

    expenseService.update(1L, updated);

    assertThat(expense.getAmount()).isEqualByComparingTo("750.00");
    assertThat(expense.getDescription()).isEqualTo("Updated repair");
    assertThat(expense.getCategory()).isEqualTo(ExpenseCategory.CLEANING_AND_MAINTENANCE);
    assertThat(expense.getProperty()).isEqualTo(otherProperty);
    verify(expenseRepository).save(expense);
  }

  @Test
  void delete_callsRepositoryDeleteById() {
    expenseService.delete(1L);

    verify(expenseRepository).deleteById(1L);
  }

  @Test
  void getTotalExpenses_returnsTotalFromRepository() {
    when(expenseRepository.getTotalExpenses()).thenReturn(new BigDecimal("1250.00"));

    BigDecimal total = expenseService.getTotalExpenses();

    assertThat(total).isEqualByComparingTo("1250.00");
  }
}
