package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bookie.model.ActivityType;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.TaxTreatment;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.FinancialCategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FinancialCategoryServiceTest {

  @Mock private FinancialCategoryRepository financialCategoryRepository;
  @Mock private FinancialActivityService financialActivityService;

  @InjectMocks private FinancialCategoryService financialCategoryService;

  private FinancialActivity teaching;

  @BeforeEach
  void setUp() {
    teaching =
        FinancialActivity.builder()
            .id(10L)
            .name("Teaching")
            .activityType(ActivityType.EMPLOYMENT)
            .taxTreatment(TaxTreatment.W2)
            .active(true)
            .build();
  }

  @Test
  void findCompatible_usesActivityTaxTreatmentAndDirection() {
    FinancialCategory wages = category(1L, "WAGES", TransactionDirection.INCOME, TaxTreatment.W2);
    when(financialActivityService.findActiveById(10L)).thenReturn(teaching);
    when(financialCategoryRepository.findAllByDirectionAndTaxTreatmentAndActiveTrueOrderByLabelAsc(
            TransactionDirection.INCOME, TaxTreatment.W2))
        .thenReturn(List.of(wages));

    assertThat(financialCategoryService.findCompatible(TransactionDirection.INCOME, 10L))
        .containsExactly(wages);
  }

  @Test
  void resolve_rejectsCategoryFromDifferentTaxTreatment() {
    FinancialCategory rental =
        category(2L, "RENTAL_INCOME", TransactionDirection.INCOME, TaxTreatment.SCHEDULE_E);
    when(financialCategoryRepository.findById(2L)).thenReturn(Optional.of(rental));

    assertThatThrownBy(
            () -> financialCategoryService.resolve(2L, null, TransactionDirection.INCOME, teaching))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("tax treatment");
  }

  @Test
  void defaultFor_mapsW2ExpenseToEmploymentFallback() {
    FinancialCategory employment =
        category(3L, "EMPLOYMENT_OTHER_EXPENSE", TransactionDirection.EXPENSE, TaxTreatment.W2);
    when(financialCategoryRepository.findByKey("EMPLOYMENT_OTHER_EXPENSE"))
        .thenReturn(Optional.of(employment));

    assertThat(financialCategoryService.defaultFor(teaching, TransactionDirection.EXPENSE))
        .isEqualTo(employment);
  }

  private FinancialCategory category(
      Long id, String key, TransactionDirection direction, TaxTreatment taxTreatment) {
    return FinancialCategory.builder()
        .id(id)
        .key(key)
        .label(key)
        .direction(direction)
        .taxTreatment(taxTreatment)
        .active(true)
        .system(true)
        .build();
  }
}
