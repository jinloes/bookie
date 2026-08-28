package com.bookie.ledger.compatibility;

import com.bookie.model.Expense;
import com.bookie.model.Income;
import java.math.BigDecimal;
import java.util.List;

public interface LegacyLedgerReadAdapter {

  List<Income> findAllIncomes();

  Income findIncomeById(Long id);

  BigDecimal getTotalIncome();

  void assertIncomeParity(List<Income> legacyIncomes);

  void assertIncomeParity(Income legacyIncome);

  List<Expense> findAllExpenses();

  Expense findExpenseById(Long id);

  BigDecimal getTotalExpenses();

  void assertExpenseParity(List<Expense> legacyExpenses);

  void assertExpenseParity(Expense legacyExpense);
}
