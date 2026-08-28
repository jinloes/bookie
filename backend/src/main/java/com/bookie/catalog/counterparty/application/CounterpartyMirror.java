package com.bookie.catalog.counterparty.application;

import com.bookie.catalog.counterparty.domain.Counterparty;

public interface CounterpartyMirror {

  void synchronize(Counterparty counterparty);

  void delete(Long counterpartyId);
}
