package com.bookie.catalog.counterparty.api;

import com.bookie.catalog.counterparty.domain.Counterparty;
import java.util.List;
import java.util.Set;

public record PayerResponse(
    Long id, String name, PayerType type, List<String> aliases, Set<String> accounts) {

  public static PayerResponse from(Counterparty counterparty) {
    if (counterparty == null) {
      return null;
    }
    return new PayerResponse(
        counterparty.getId(),
        counterparty.getName(),
        PayerType.from(counterparty.getType()),
        counterparty.getAliases(),
        counterparty.getAccounts());
  }
}
