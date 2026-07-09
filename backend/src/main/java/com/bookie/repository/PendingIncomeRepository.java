package com.bookie.repository;

import com.bookie.model.ExpenseSource;
import com.bookie.model.PendingIncome;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PendingIncomeRepository extends JpaRepository<PendingIncome, Long> {

  Optional<PendingIncome> findBySourceId(String sourceId);

  boolean existsBySourceTypeAndSourceId(ExpenseSource sourceType, String sourceId);
}
