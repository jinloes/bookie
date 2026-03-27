package com.bookie.repository;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByPropertyNameIgnoreCase(String propertyName);

    List<Expense> findByCategory(ExpenseCategory category);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e")
    BigDecimal getTotalExpenses();
}
