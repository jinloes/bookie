package com.bookie.catalog.counterparty.application;

public interface CounterpartyClassificationReferences {

  void removeCounterpartyReferences(Long counterpartyId);

  void requireNoCounterpartyReferences(Long counterpartyId);
}
