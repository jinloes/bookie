package com.bookie.catalog.financialcategory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

class FinancialCategoryMapperTest {

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void mapsTheRetainedEntityWithoutANeutralCategoryOutsideARequest() {
    FinancialCategory category =
        FinancialCategory.builder()
            .id(41L)
            .key("legacy-key")
            .label("Legacy label")
            .direction(TransactionDirection.EXPENSE)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .taxLine("19")
            .active(true)
            .system(false)
            .build();

    assertThat(FinancialCategoryMapper.toResponse(category))
        .isEqualTo(
            new FinancialCategoryResponse(
                41L,
                "legacy-key",
                "Legacy label",
                TransactionDirection.EXPENSE,
                LegacyTaxTreatment.SCHEDULE_E,
                "19",
                true,
                false,
                null));
  }

  @Test
  void resolvesTheNeutralCategoryFromTheHandlingRequestsCatalog() {
    CategoryCatalog catalog = mock(CategoryCatalog.class);
    when(catalog.findByLegacyCategoryId(41L))
        .thenReturn(
            Optional.of(
                LegacyCategoryMap.builder()
                    .legacyCategoryId(41L)
                    .neutralCategory(NeutralCategory.builder().id(90L).build())
                    .build()));
    bindRequest(catalog);

    assertThat(FinancialCategoryMapper.toResponse(category()).neutralCategoryId()).isEqualTo(90L);
  }

  @Test
  void omitsTheNeutralCategoryWhenTheRequestContextHasNoCatalog() {
    bindRequest(null);

    assertThat(FinancialCategoryMapper.toResponse(category()).neutralCategoryId()).isNull();
  }

  @Test
  void omitsTheNeutralCategoryForANonServletRequest() {
    RequestContextHolder.setRequestAttributes(mock(RequestAttributes.class));

    assertThat(FinancialCategoryMapper.toResponse(category()).neutralCategoryId()).isNull();
  }

  private void bindRequest(CategoryCatalog catalog) {
    StaticWebApplicationContext context = new StaticWebApplicationContext();
    if (catalog != null) {
      context.getBeanFactory().registerSingleton("categoryCatalog", catalog);
    }
    context.refresh();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  private FinancialCategory category() {
    return FinancialCategory.builder()
        .id(41L)
        .key("legacy-key")
        .label("Legacy label")
        .direction(TransactionDirection.EXPENSE)
        .taxTreatment(TaxTreatment.SCHEDULE_E)
        .build();
  }

  @Test
  void preservesANullNestedCategory() {
    assertThat(FinancialCategoryMapper.toResponse(null)).isNull();
  }
}
