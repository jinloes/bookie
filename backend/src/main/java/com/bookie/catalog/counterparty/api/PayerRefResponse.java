package com.bookie.catalog.counterparty.api;

import com.bookie.catalog.counterparty.domain.Counterparty;

public record PayerRefResponse(Long id, String name, PayerType type) {

  public static PayerRefResponse from(Counterparty counterparty) {
    if (counterparty == null) {
      return null;
    }
    return new PayerRefResponse(
        counterparty.getId(), counterparty.getName(), PayerType.from(counterparty.getType()));
  }
}
