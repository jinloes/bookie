package com.bookie.ledger.compatibility;

import com.bookie.model.Expense;
import com.bookie.model.Income;

public interface LegacyLedgerSynchronizer {

  void synchronize(Income income);

  void synchronize(Expense expense);

  void tombstoneIncome(Long incomeId);

  void tombstoneExpense(Long expenseId);
}
