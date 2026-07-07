package com.bookie.model;

public enum PropertyType {
  SINGLE_FAMILY("Single Family"),
  MULTI_FAMILY("Multi-Family"),
  CONDO("Condo"),
  TOWNHOUSE("Townhouse"),
  COMMERCIAL("Commercial"),
  OTHER("Other");

  public final String label;

  PropertyType(String label) {
    this.label = label;
  }
}
