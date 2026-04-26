package com.bookie.repository;

import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingExpenseRepository extends JpaRepository<PendingExpense, Long> {
  List<PendingExpense> findBySourceIdIn(Collection<String> sourceIds);

  Optional<PendingExpense> findBySourceId(String sourceId);

  List<PendingExpense> findByStatus(PendingExpenseStatus status);
}
