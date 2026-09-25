package com.bookie.catalog.counterparty.api;

import com.bookie.catalog.counterparty.application.UpsertCounterpartyCommand;
import java.util.List;
import java.util.Set;

public record UpsertCounterpartyRequest(
    String name, CounterpartyTypeDto type, List<String> aliases, Set<String> accounts) {

  UpsertCounterpartyCommand toCommand() {
    return new UpsertCounterpartyCommand(name, type.toDomain(), aliases, accounts);
  }
}
