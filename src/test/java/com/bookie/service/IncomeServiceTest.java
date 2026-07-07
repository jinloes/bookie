package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.repository.IncomeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

  @Mock private IncomeRepository incomeRepository;
  @Mock private PropertyService propertyService;

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

  @Nested
  class Create {

    @Test
    void withProperty_resolvesPropertyAndSaves() {
      CreateIncomeRequest req =
          new CreateIncomeRequest(
              new BigDecimal("1200.00"),
              "Monthly rent",
              LocalDate.of(2024, 1, 1),
              "Rent",
              1L,
              ExpenseSource.MANUAL,
              null,
              null);
      when(propertyService.findById(1L)).thenReturn(property);
      when(incomeRepository.save(any())).thenReturn(income);

      Income result = incomeService.create(req);

      assertThat(result).isEqualTo(income);
      verify(propertyService).findById(1L);
      verify(incomeRepository).save(any());
    }

    @Test
    void withNullPropertyId_savesWithoutProperty() {
      CreateIncomeRequest req =
          new CreateIncomeRequest(
              new BigDecimal("1200.00"),
              "Monthly rent",
              LocalDate.of(2024, 1, 1),
              "Rent",
              null,
              null,
              null,
              null);
      when(incomeRepository.save(any())).thenReturn(income);

      Income result = incomeService.create(req);

      assertThat(result).isEqualTo(income);
      verify(incomeRepository).save(any());
    }
  }

  @Nested
  class Update {

    @Test
    void updatesFieldsAndSaves() {
      Property otherProp =
          Property.builder()
              .id(2L)
              .name("456 Oak Ave")
              .address("456 Oak Ave")
              .type(PropertyType.SINGLE_FAMILY)
              .build();
      UpdateIncomeRequest req =
          new UpdateIncomeRequest(
              new BigDecimal("1400.00"), "Updated rent", LocalDate.of(2024, 2, 1), "Rent", 2L);
      when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));
      when(propertyService.findById(2L)).thenReturn(otherProp);
      when(incomeRepository.save(income)).thenReturn(income);

      incomeService.update(1L, req);

      assertThat(income.getAmount()).isEqualByComparingTo("1400.00");
      assertThat(income.getDescription()).isEqualTo("Updated rent");
      assertThat(income.getSource()).isEqualTo("Rent");
      assertThat(income.getProperty()).isEqualTo(otherProp);
      verify(incomeRepository).save(income);
    }

    @Test
    void withNullPropertyId_clearsProperty() {
      UpdateIncomeRequest req =
          new UpdateIncomeRequest(
              new BigDecimal("1200.00"), "Monthly rent", LocalDate.of(2024, 1, 1), "Rent", null);
      when(incomeRepository.findById(1L)).thenReturn(Optional.of(income));
      when(incomeRepository.save(income)).thenReturn(income);

      incomeService.update(1L, req);

      assertThat(income.getProperty()).isNull();
    }
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
