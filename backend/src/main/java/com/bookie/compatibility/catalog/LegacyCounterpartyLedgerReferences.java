package com.bookie.compatibility.catalog;

import com.bookie.catalog.counterparty.application.CounterpartyLedgerReferences;
import com.bookie.ledger.application.LedgerLifecycle;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyCounterpartyLedgerReferences implements CounterpartyLedgerReferences {

  private final ExpenseRepository expenseRepository;
  private final IncomeRepository incomeRepository;
  private final LedgerLifecycle ledgerLifecycle;

  @Override
  public void detachCounterparty(Long counterpartyId) {
    ledgerLifecycle.detachCounterparty(counterpartyId);
    expenseRepository.clearPayerById(counterpartyId);
    incomeRepository.clearPayerById(counterpartyId);
  }

  @Override
  public void requireNoReferences(Long counterpartyId) {
    if (expenseRepository.countByPayerId(counterpartyId) != 0
        || incomeRepository.countByPayerId(counterpartyId) != 0
        || ledgerLifecycle.countCounterpartyReferences(counterpartyId) != 0) {
      throw new IllegalStateException(
          "Ledger records still reference counterparty: " + counterpartyId);
    }
  }
}
