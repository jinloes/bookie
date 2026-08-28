package com.bookie.catalog.activity.domain;

public enum ActivityType {
  RENTAL("Rental"),
  EMPLOYMENT("Employment"),
  SELF_EMPLOYMENT("Self-employment"),
  OTHER("Other");

  public final String label;

  ActivityType(String label) {
    this.label = label;
  }
}
