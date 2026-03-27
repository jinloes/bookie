package com.bookie.service;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private ExpenseService expenseService;

    private Expense expense;

    @BeforeEach
    void setUp() {
        expense = new Expense(1L, new BigDecimal("500.00"), "Roof repair", LocalDate.of(2024, 1, 15),
                ExpenseCategory.REPAIRS, "123 Main St");
    }

    @Test
    void findAll_returnsAllExpenses() {
        when(expenseRepository.findAll()).thenReturn(List.of(expense));

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
        Expense updated = new Expense(null, new BigDecimal("750.00"), "Updated repair", LocalDate.of(2024, 2, 1),
                ExpenseCategory.CLEANING_AND_MAINTENANCE, "456 Oak Ave");
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(expense)).thenReturn(expense);

        expenseService.update(1L, updated);

        assertThat(expense.getAmount()).isEqualByComparingTo("750.00");
        assertThat(expense.getDescription()).isEqualTo("Updated repair");
        assertThat(expense.getCategory()).isEqualTo(ExpenseCategory.CLEANING_AND_MAINTENANCE);
        assertThat(expense.getPropertyName()).isEqualTo("456 Oak Ave");
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