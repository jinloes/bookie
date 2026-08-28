package com.bookie.compatibility.catalog;

import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.model.FinancialCategory;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JpaCategoryCatalog implements CategoryCatalog {

  private final EntityManager entityManager;

  @Override
  public Optional<LegacyCategoryMap> findByLegacyCategoryId(Long legacyCategoryId) {
    return Optional.ofNullable(entityManager.find(LegacyCategoryMap.class, legacyCategoryId));
  }

  @Override
  public Optional<LegacyCategoryView> findLegacyCategoryById(Long legacyCategoryId) {
    return Optional.ofNullable(entityManager.find(FinancialCategory.class, legacyCategoryId))
        .map(JpaCategoryCatalog::toView);
  }

  private static LegacyCategoryView toView(FinancialCategory category) {
    return LegacyCategoryView.builder()
        .id(category.getId())
        .key(category.getKey())
        .label(category.getLabel())
        .direction(category.getDirection())
        .taxTreatment(LegacyTaxTreatment.valueOf(category.getTaxTreatment().name()))
        .taxLine(category.getTaxLine())
        .active(category.isActive())
        .system(category.isSystem())
        .build();
  }
}
