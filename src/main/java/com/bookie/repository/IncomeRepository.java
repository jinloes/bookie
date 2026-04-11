package com.bookie.repository;

import com.bookie.model.Income;
import com.bookie.model.Property;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeRepository extends JpaRepository<Income, Long> {

  List<Income> findByProperty(Property property);

  List<Income> findBySourceIdIn(List<String> sourceIds);

  @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i")
  BigDecimal getTotalIncome();
}
