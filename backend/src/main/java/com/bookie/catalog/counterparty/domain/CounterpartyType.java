package com.bookie.catalog.counterparty.domain;

public enum CounterpartyType {
  PERSON("Person"),
  COMPANY("Company");

  public final String label;

  CounterpartyType(String label) {
    this.label = label;
  }
}
