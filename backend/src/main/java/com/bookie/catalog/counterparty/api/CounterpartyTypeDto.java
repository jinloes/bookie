package com.bookie.catalog.counterparty.api;

import com.bookie.catalog.counterparty.domain.CounterpartyType;

public enum CounterpartyTypeDto {
  PERSON("Person"),
  COMPANY("Company");

  public final String label;

  CounterpartyTypeDto(String label) {
    this.label = label;
  }

  CounterpartyType toDomain() {
    return CounterpartyType.valueOf(name());
  }

  static CounterpartyTypeDto from(CounterpartyType type) {
    return type == null ? null : valueOf(type.name());
  }
}
