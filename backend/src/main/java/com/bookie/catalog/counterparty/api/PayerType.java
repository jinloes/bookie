package com.bookie.catalog.counterparty.api;

import com.bookie.catalog.counterparty.domain.CounterpartyType;

public enum PayerType {
  PERSON("Person"),
  COMPANY("Company");

  public final String label;

  PayerType(String label) {
    this.label = label;
  }

  CounterpartyType toDomain() {
    return CounterpartyType.valueOf(name());
  }

  static PayerType from(CounterpartyType type) {
    return type == null ? null : valueOf(type.name());
  }
}
