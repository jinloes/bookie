package com.bookie.catalog.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryStore categoryStore;

  private CategoryService service;

  @BeforeEach
  void setUp() {
    service = new CategoryService(categoryStore);
  }

  @Test
  void delegatesActiveAndDirectionQueriesToStore() {
    NeutralCategory wages =
        NeutralCategory.builder()
            .id(1L)
            .key("WAGES")
            .label("Wages")
            .direction(TransactionDirection.INCOME)
            .active(true)
            .build();
    when(categoryStore.findAllByActiveTrueOrderByLabelAsc()).thenReturn(List.of(wages));
    when(categoryStore.findById(1L)).thenReturn(Optional.of(wages));
    when(categoryStore.findByKey("WAGES")).thenReturn(Optional.of(wages));

    assertThat(service.findAllActive()).containsExactly(wages);
    assertThat(service.findById(1L)).contains(wages);
    assertThat(service.findByKey("WAGES")).contains(wages);
  }
}
