package com.bookie.controller;

import com.bookie.model.EmailType;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Income;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ApiResponses {

  private ApiResponses() {}

  public record ApiErrorResponse(String code, String message, Map<String, Object> details) {}

  public record TotalAmountResponse(BigDecimal total) {}

  public record EnumOptionResponse(String value, String label) {}

  public record PropertyRefResponse(Long id, String name) {
    public static PropertyRefResponse from(Property property) {
      if (property == null) {
        return null;
      }
      return new PropertyRefResponse(property.getId(), property.getName());
    }
  }

  public record PayerRefResponse(Long id, String name, PayerType type) {
    public static PayerRefResponse from(Payer payer) {
      if (payer == null) {
        return null;
      }
      return new PayerRefResponse(payer.getId(), payer.getName(), payer.getType());
    }
  }

  public record ExpenseResponse(
      Long id,
      BigDecimal amount,
      String description,
      LocalDate date,
      String category,
      PropertyRefResponse property,
      ExpenseSource sourceType,
      String sourceId,
      PayerRefResponse payer,
      String receiptOneDriveId,
      String receiptFileName) {
    public static ExpenseResponse from(Expense expense) {
      if (expense == null) {
        return null;
      }
      return new ExpenseResponse(
          expense.getId(),
          expense.getAmount(),
          expense.getDescription(),
          expense.getDate(),
          expense.getCategory() != null ? expense.getCategory().name() : null,
          PropertyRefResponse.from(expense.getProperty()),
          expense.getSourceType(),
          expense.getSourceId(),
          PayerRefResponse.from(expense.getPayer()),
          expense.getReceiptOneDriveId(),
          expense.getReceiptFileName());
    }
  }

  public record IncomeResponse(
      Long id,
      BigDecimal amount,
      String description,
      LocalDate date,
      String source,
      String sourceId,
      ExpenseSource sourceType,
      String receiptOneDriveId,
      String receiptFileName,
      PropertyRefResponse property,
      PayerRefResponse payer) {
    public static IncomeResponse from(Income income) {
      if (income == null) {
        return null;
      }
      return new IncomeResponse(
          income.getId(),
          income.getAmount(),
          income.getDescription(),
          income.getDate(),
          income.getSource(),
          income.getSourceId(),
          income.getSourceType(),
          income.getReceiptOneDriveId(),
          income.getReceiptFileName(),
          PropertyRefResponse.from(income.getProperty()),
          PayerRefResponse.from(income.getPayer()));
    }
  }

  public record VenmoIncomeImportResponse(
      int totalRows,
      int importedRows,
      int skippedSenderRows,
      int skippedOutgoingRows,
      int skippedDuplicateRows,
      int skippedInvalidRows,
      String senderFilter) {}

  public record PropertyResponse(
      Long id, String name, String address, PropertyType type, String notes, Set<String> accounts) {
    public static PropertyResponse from(Property property) {
      if (property == null) {
        return null;
      }
      return new PropertyResponse(
          property.getId(),
          property.getName(),
          property.getAddress(),
          property.getType(),
          property.getNotes(),
          property.getAccounts());
    }
  }

  public record PayerResponse(
      Long id, String name, PayerType type, List<String> aliases, Set<String> accounts) {
    public static PayerResponse from(Payer payer) {
      if (payer == null) {
        return null;
      }
      return new PayerResponse(
          payer.getId(), payer.getName(), payer.getType(), payer.getAliases(), payer.getAccounts());
    }
  }

  public record PendingExpenseResponse(
      Long id,
      String sourceId,
      ExpenseSource sourceType,
      EmailType emailType,
      String subject,
      PendingExpenseStatus status,
      BigDecimal amount,
      String description,
      LocalDate date,
      String category,
      String propertyName,
      String payerName,
      String errorMessage,
      LocalDateTime createdAt) {
    public static PendingExpenseResponse from(PendingExpense pendingExpense) {
      if (pendingExpense == null) {
        return null;
      }
      return new PendingExpenseResponse(
          pendingExpense.getId(),
          pendingExpense.getSourceId(),
          pendingExpense.getSourceType(),
          pendingExpense.getEmailType(),
          pendingExpense.getSubject(),
          pendingExpense.getStatus(),
          pendingExpense.getAmount(),
          pendingExpense.getDescription(),
          pendingExpense.getDate(),
          pendingExpense.getCategory(),
          pendingExpense.getPropertyName(),
          pendingExpense.getPayerName(),
          pendingExpense.getErrorMessage(),
          pendingExpense.getCreatedAt());
    }
  }
}
