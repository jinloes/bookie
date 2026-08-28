package com.bookie.catalog.category.application;

import com.bookie.catalog.category.domain.LegacyCategoryMap;
import java.util.Optional;

public interface CategoryCatalog {

  Optional<LegacyCategoryMap> findByLegacyCategoryId(Long legacyCategoryId);

  Optional<LegacyCategoryView> findLegacyCategoryById(Long legacyCategoryId);
}
