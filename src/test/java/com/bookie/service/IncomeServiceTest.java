package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bookie.model.Income;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.repository.IncomeRepository;
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
class IncomeServiceTest {

  @Mock private IncomeRepository incomeRepository;

  @InjectMocks private IncomeService incomeService;

  private Income income;
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
    income =
        Income.builder()
            .id(1L)
            .amount(new BigDecimal("1200.00"))
            .description("Monthly rent")
            .date(LocalDate.of(2024, 1, 1))
            .source("Rent")
            .property(property)
            .build();
  }

  @Test
  void findAll_returnsAllIncomes() {
    when(incomeRepository.findAll(any(Sort.class))).thenReturn(List.of(income));

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
    Property otherProp =
        Property.builder()
            .id(2L)
            .name("456 Oak Ave")
            .address("456 Oak Ave")
            .type(PropertyType.SINGLE_FAMILY)
            .build();
    Income updated =
        Income.builder()
            .amount(new BigDecimal("1400.00"))
            .description("Updated rent")
            .date(LocalDate.of(2024, 2, 1))
            .source("Rent")
            .property(otherProp)
            .build();
    when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));
    when(incomeRepository.save(income)).thenReturn(income);

    incomeService.update(1L, updated);

    assertThat(income.getAmount()).isEqualByComparingTo("1400.00");
    assertThat(income.getDescription()).isEqualTo("Updated rent");
    assertThat(income.getSource()).isEqualTo("Rent");
    assertThat(income.getProperty()).isEqualTo(otherProp);
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
