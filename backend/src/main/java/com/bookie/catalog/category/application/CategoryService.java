package com.bookie.catalog.category.application;

import com.bookie.catalog.category.domain.NeutralCategory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryService implements CategoryCatalog {

  private final CategoryStore categoryStore;

  @Override
  public List<NeutralCategory> findAllActive() {
    return categoryStore.findAllByActiveTrueOrderByLabelAsc();
  }

  @Override
  public Optional<NeutralCategory> findById(Long id) {
    return categoryStore.findById(id);
  }

  @Override
  public Optional<NeutralCategory> findByKey(String key) {
    return categoryStore.findByKey(key);
  }
}
