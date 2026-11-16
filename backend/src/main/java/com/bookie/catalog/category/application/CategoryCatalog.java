package com.bookie.catalog.category.application;

import com.bookie.catalog.category.domain.NeutralCategory;
import java.util.List;
import java.util.Optional;

public interface CategoryCatalog {

  List<NeutralCategory> findAllActive();

  Optional<NeutralCategory> findById(Long id);

  Optional<NeutralCategory> findByKey(String key);
}
