package com.bookie.catalog.counterparty.application;

public interface CounterpartyLedgerReferences {

  void detachCounterparty(Long counterpartyId);

  void requireNoReferences(Long counterpartyId);
}
