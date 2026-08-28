package com.bookie.intake.infrastructure;

import com.bookie.intake.domain.InboxItem;
import com.bookie.model.ExpenseSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface InboxItemRepository extends JpaRepository<InboxItem, Long> {

  @Override
  @EntityGraph(attributePaths = "artifacts")
  Optional<InboxItem> findById(Long id);

  @EntityGraph(attributePaths = "artifacts")
  Optional<InboxItem> findByOriginAndLegacySourceId(ExpenseSource origin, String legacySourceId);

  @Override
  @EntityGraph(attributePaths = "artifacts")
  List<InboxItem> findAll(Sort sort);
}
