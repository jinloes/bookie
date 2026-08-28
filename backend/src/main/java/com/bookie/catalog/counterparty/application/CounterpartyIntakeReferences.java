package com.bookie.catalog.counterparty.application;

public interface CounterpartyIntakeReferences {

  void detachCounterparty(Long counterpartyId);

  void requireNoReferences(Long counterpartyId);
}
