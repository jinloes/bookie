package com.bookie.ledger.infrastructure;

import com.bookie.catalog.counterparty.application.CounterpartyDeleting;
import com.bookie.ledger.application.LedgerLifecycle;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerCounterpartyReferences {

  private final LedgerLifecycle ledgerLifecycle;

  @EventListener
  public void onCounterpartyDeleting(CounterpartyDeleting event) {
    detachCounterparty(event.counterpartyId());
    requireNoReferences(event.counterpartyId());
  }

  public void detachCounterparty(Long counterpartyId) {
    ledgerLifecycle.detachCounterparty(counterpartyId);
  }

  public void requireNoReferences(Long counterpartyId) {
    if (ledgerLifecycle.countCounterpartyReferences(counterpartyId) != 0) {
      throw new IllegalStateException(
          "Ledger records still reference counterparty: " + counterpartyId);
    }
  }
}
