package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.bookie.model.CreateExpenseRequest;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Payer;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
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
      when(propertyService.findById(1L)).thenReturn(property);
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
  }

  @Nested
  class UpdateWithRequest {

    @Test
    void update_resolvesPropertyAndPayerAndUpdatesExpense() {
      Payer payer = Payer.builder().id(2L).name("John").build();
      when(propertyService.findById(1L)).thenReturn(property);
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
