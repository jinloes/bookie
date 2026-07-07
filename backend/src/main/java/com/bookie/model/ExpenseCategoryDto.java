package com.bookie.model;

public record ExpenseCategoryDto(String value, String label, int scheduleELine) {
  public static ExpenseCategoryDto from(ExpenseCategory category) {
    return new ExpenseCategoryDto(category.name(), category.label, category.scheduleELine);
  }
}
