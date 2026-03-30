package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

  @Mock private ExpenseRepository expenseRepository;
  @Mock private PropertyHistoryService propertyHistoryService;

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
