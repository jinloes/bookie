package com.bookie.repository;

import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingExpenseRepository extends JpaRepository<PendingExpense, Long> {

  // The list endpoint serializes pending expenses including their @ElementCollection of
  // unrecognized aliases. Fetch the collection eagerly in one query so serialization can't trip
  // a LazyInitializationException when the session is no longer available.
  @Override
  @EntityGraph(attributePaths = "unrecognizedAliases")
  List<PendingExpense> findAll(Sort sort);

  List<PendingExpense> findBySourceIdIn(Collection<String> sourceIds);

  Optional<PendingExpense> findBySourceId(String sourceId);

  List<PendingExpense> findByStatus(PendingExpenseStatus status);
}
