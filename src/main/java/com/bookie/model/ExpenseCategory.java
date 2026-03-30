package com.bookie.model;

public enum ExpenseCategory {
  ADVERTISING(5, "Advertising"),
  AUTO_AND_TRAVEL(6, "Auto and travel"),
  CLEANING_AND_MAINTENANCE(7, "Cleaning and maintenance"),
  COMMISSIONS(8, "Commissions"),
  INSURANCE(9, "Insurance"),
  LEGAL_AND_PROFESSIONAL(10, "Legal and professional fees"),
  MANAGEMENT_FEES(11, "Management fees"),
  MORTGAGE_INTEREST(12, "Mortgage interest"),
  OTHER_INTEREST(13, "Other interest"),
  REPAIRS(14, "Repairs"),
  SUPPLIES(15, "Supplies"),
  TAXES(16, "Taxes"),
  UTILITIES(17, "Utilities"),
  DEPRECIATION(18, "Depreciation"),
  OTHER(19, "Other");

  public final int scheduleELine;
  public final String label;

  ExpenseCategory(int scheduleELine, String label) {
    this.scheduleELine = scheduleELine;
    this.label = label;
  }
}
