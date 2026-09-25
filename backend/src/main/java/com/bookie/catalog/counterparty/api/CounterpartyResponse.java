package com.bookie.catalog.counterparty.api;

import com.bookie.catalog.counterparty.domain.Counterparty;
import java.util.List;
import java.util.Set;

public record CounterpartyResponse(
    Long id, String name, CounterpartyTypeDto type, List<String> aliases, Set<String> accounts) {

  public static CounterpartyResponse from(Counterparty counterparty) {
    if (counterparty == null) {
      return null;
    }
    return new CounterpartyResponse(
        counterparty.getId(),
        counterparty.getName(),
        CounterpartyTypeDto.from(counterparty.getType()),
        counterparty.getAliases(),
        counterparty.getAccounts());
  }
}
