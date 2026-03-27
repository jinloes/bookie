package com.bookie.service;

import com.bookie.model.Income;
import com.bookie.repository.IncomeRepository;
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
class IncomeServiceTest {

    @Mock
    private IncomeRepository incomeRepository;

    @InjectMocks
    private IncomeService incomeService;

    private Income income;

    @BeforeEach
    void setUp() {
        income = new Income(1L, new BigDecimal("1200.00"), "Monthly rent", LocalDate.of(2024, 1, 1),
                "Rent", "123 Main St");
    }

    @Test
    void findAll_returnsAllIncomes() {
        when(incomeRepository.findAll()).thenReturn(List.of(income));

        List<Income> result = incomeService.findAll();

        assertThat(result).hasSize(1).containsExactly(income);
    }

    @Test
    void findById_found_returnsIncome() {
        when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));

        Income result = incomeService.findById(1L);

        assertThat(result).isEqualTo(income);
    }

    @Test
    void findById_notFound_throwsException() {
        when(incomeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incomeService.findById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    @Test
    void save_persistsAndReturnsIncome() {
        when(incomeRepository.save(income)).thenReturn(income);

        Income result = incomeService.save(income);

        assertThat(result).isEqualTo(income);
        verify(incomeRepository).save(income);
    }

    @Test
    void update_updatesFieldsAndSaves() {
        Income updated = new Income(null, new BigDecimal("1400.00"), "Updated rent", LocalDate.of(2024, 2, 1),
                "Rent", "456 Oak Ave");
        when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));
        when(incomeRepository.save(income)).thenReturn(income);

        incomeService.update(1L, updated);

        assertThat(income.getAmount()).isEqualByComparingTo("1400.00");
        assertThat(income.getDescription()).isEqualTo("Updated rent");
        assertThat(income.getSource()).isEqualTo("Rent");
        assertThat(income.getPropertyName()).isEqualTo("456 Oak Ave");
        verify(incomeRepository).save(income);
    }

    @Test
    void delete_callsRepositoryDeleteById() {
        incomeService.delete(1L);

        verify(incomeRepository).deleteById(1L);
    }

    @Test
    void getTotalIncome_returnsTotalFromRepository() {
        when(incomeRepository.getTotalIncome()).thenReturn(new BigDecimal("3600.00"));

        BigDecimal total = incomeService.getTotalIncome();

        assertThat(total).isEqualByComparingTo("3600.00");
    }
}