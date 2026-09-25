package com.bookie.ledger.infrastructure;

import com.bookie.catalog.counterparty.application.CounterpartyLedgerReferences;
import com.bookie.ledger.application.LedgerLifecycle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerCounterpartyReferences implements CounterpartyLedgerReferences {

  private final LedgerLifecycle ledgerLifecycle;

  @Override
  public void detachCounterparty(Long counterpartyId) {
    ledgerLifecycle.detachCounterparty(counterpartyId);
  }

  @Override
  public void requireNoReferences(Long counterpartyId) {
    if (ledgerLifecycle.countCounterpartyReferences(counterpartyId) != 0) {
      throw new IllegalStateException(
          "Ledger records still reference counterparty: " + counterpartyId);
    }
  }
}
