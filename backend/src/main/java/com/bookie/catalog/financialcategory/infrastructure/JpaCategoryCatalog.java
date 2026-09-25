package com.bookie.catalog.financialcategory.infrastructure;

import com.bookie.catalog.category.application.CategoryCatalog;
import com.bookie.catalog.category.application.LegacyCategoryView;
import com.bookie.catalog.category.domain.LegacyCategoryMap;
import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
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
        .map(this::toView);
  }

  @Override
  public Optional<LegacyCategoryView> findByKey(String key) {
    return entityManager
        .createQuery(
            "SELECT category FROM FinancialCategory category WHERE category.key = :key",
            FinancialCategory.class)
        .setParameter("key", key)
        .getResultStream()
        .findFirst()
        .map(this::toView);
  }

  private LegacyCategoryView toView(FinancialCategory category) {
    return LegacyCategoryView.builder()
        .id(category.getId())
        .key(category.getKey())
        .label(category.getLabel())
        .direction(category.getDirection())
        .taxTreatment(LegacyTaxTreatment.valueOf(category.getTaxTreatment().name()))
        .taxLine(category.getTaxLine())
        .active(category.isActive())
        .system(category.isSystem())
        .neutralCategoryId(
            findByLegacyCategoryId(category.getId())
                .map(map -> map.getNeutralCategory().getId())
                .orElse(null))
        .build();
  }
}
