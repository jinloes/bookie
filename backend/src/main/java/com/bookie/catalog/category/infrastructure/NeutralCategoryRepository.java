package com.bookie.catalog.category.infrastructure;

import com.bookie.catalog.category.application.CategoryStore;
import com.bookie.catalog.category.domain.NeutralCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NeutralCategoryRepository
    extends JpaRepository<NeutralCategory, Long>, CategoryStore {

  @Override
  List<NeutralCategory> findAllByActiveTrueOrderByLabelAsc();

  @Override
  Optional<NeutralCategory> findById(Long id);

  @Override
  Optional<NeutralCategory> findByKey(String key);
}
