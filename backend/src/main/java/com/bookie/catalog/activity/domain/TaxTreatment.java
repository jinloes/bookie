package com.bookie.catalog.activity.domain;

public enum TaxTreatment {
  SCHEDULE_E("Schedule E"),
  SCHEDULE_C("Schedule C"),
  W2("W-2 employment"),
  NONE("No tax report");

  public final String label;

  TaxTreatment(String label) {
    this.label = label;
  }
}
