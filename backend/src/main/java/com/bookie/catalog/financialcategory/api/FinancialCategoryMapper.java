package com.bookie.catalog.financialcategory.api;

import com.bookie.catalog.category.api.FinancialCategoryResponse;
import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

/** Converts the retained legacy category entity at the compatibility boundary. */
public final class FinancialCategoryMapper {

  private FinancialCategoryMapper() {}

  public static FinancialCategoryResponse toResponse(FinancialCategory category) {
    if (category == null) {
      return null;
    }
    return new FinancialCategoryResponse(
        category.getId(),
        category.getKey(),
        category.getLabel(),
        category.getDirection(),
        LegacyTaxTreatment.valueOf(category.getTaxTreatment().name()),
        category.getTaxLine(),
        category.isActive(),
        category.isSystem(),
        neutralCategoryId(category.getId()));
  }

  // Responses are assembled by static method references in HTTP handlers, so the catalog is
  // resolved from the handling request's application context rather than injected; outside a
  // request (or without a catalog bean) the neutral category is simply omitted.
  private static Long neutralCategoryId(Long legacyCategoryId) {
    return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
        .filter(ServletRequestAttributes.class::isInstance)
        .map(attributes -> ((ServletRequestAttributes) attributes).getRequest())
        .map(RequestContextUtils::findWebApplicationContext)
        .map(context -> context.getBeanProvider(CategoryCatalog.class).getIfAvailable())
        .flatMap(catalog -> catalog.findByLegacyCategoryId(legacyCategoryId))
        .map(map -> map.getNeutralCategory().getId())
        .orElse(null);
  }
}
