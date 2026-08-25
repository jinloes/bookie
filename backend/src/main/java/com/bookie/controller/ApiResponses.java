package com.bookie.controller;

import com.bookie.model.EmailType;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.HouseholdMember;
import com.bookie.model.Income;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.model.PendingIncome;
import com.bookie.model.PendingIncomeStatus;
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

  public record ActivityCashflowResponse(
      FinancialActivityResponse activity,
      BigDecimal income,
      BigDecimal expenses,
      BigDecimal netCashflow) {}

  public record CashflowSummaryResponse(
      LocalDate from,
      LocalDate to,
      BigDecimal totalIncome,
      BigDecimal totalExpenses,
      BigDecimal netCashflow,
      List<ActivityCashflowResponse> activities) {}

  public record CategoryTotalResponse(FinancialCategoryResponse category, BigDecimal total) {}

  public record ScheduleEActivityResponse(
      FinancialActivityResponse activity,
      BigDecimal rentalIncome,
      BigDecimal expenses,
      BigDecimal netIncome,
      List<CategoryTotalResponse> categories) {}

  public record ScheduleEReportResponse(
      int year,
      BigDecimal rentalIncome,
      BigDecimal expenses,
      BigDecimal netIncome,
      List<ScheduleEActivityResponse> activities) {}

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

  public record HouseholdMemberRefResponse(Long id, String name) {
    public static HouseholdMemberRefResponse from(HouseholdMember member) {
      if (member == null) {
        return null;
      }
      return new HouseholdMemberRefResponse(member.getId(), member.getName());
    }
  }

  public record HouseholdMemberResponse(Long id, String name, boolean active, boolean system) {
    public static HouseholdMemberResponse from(HouseholdMember member) {
      if (member == null) {
        return null;
      }
      return new HouseholdMemberResponse(
          member.getId(), member.getName(), member.isActive(), member.getSystemKey() != null);
    }
  }

  public record FinancialActivityResponse(
      Long id,
      String name,
      com.bookie.model.ActivityType activityType,
      com.bookie.model.TaxTreatment taxTreatment,
      HouseholdMemberRefResponse owner,
      PropertyRefResponse property,
      boolean active,
      boolean needsClassification) {
    public static FinancialActivityResponse from(FinancialActivity activity) {
      if (activity == null) {
        return null;
      }

      return new FinancialActivityResponse(
          activity.getId(),
          activity.getName(),
          activity.getActivityType(),
          activity.getTaxTreatment(),
          HouseholdMemberRefResponse.from(activity.getOwner()),
          PropertyRefResponse.from(activity.getProperty()),
          activity.isActive(),
          activity.getSystemKey() != null);
    }
  }

  public record FinancialCategoryResponse(
      Long id,
      String key,
      String label,
      com.bookie.model.TransactionDirection direction,
      com.bookie.model.TaxTreatment taxTreatment,
      String taxLine,
      boolean active,
      boolean system) {
    public static FinancialCategoryResponse from(FinancialCategory category) {
      if (category == null) {
        return null;
      }
      return new FinancialCategoryResponse(
          category.getId(),
          category.getKey(),
          category.getLabel(),
          category.getDirection(),
          category.getTaxTreatment(),
          category.getTaxLine(),
          category.isActive(),
          category.isSystem());
    }
  }

  public record ExpenseResponse(
      Long id,
      BigDecimal amount,
      String description,
      LocalDate date,
      String category,
      FinancialCategoryResponse financialCategory,
      PropertyRefResponse property,
      ExpenseSource sourceType,
      String sourceId,
      PayerRefResponse payer,
      FinancialActivityResponse activity,
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
          FinancialCategoryResponse.from(expense.getFinancialCategory()),
          PropertyRefResponse.from(expense.getProperty()),
          expense.getSourceType(),
          expense.getSourceId(),
          PayerRefResponse.from(expense.getPayer()),
          FinancialActivityResponse.from(expense.getActivity()),
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
      PayerRefResponse payer,
      FinancialActivityResponse activity,
      FinancialCategoryResponse financialCategory) {
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
          PayerRefResponse.from(income.getPayer()),
          FinancialActivityResponse.from(income.getActivity()),
          FinancialCategoryResponse.from(income.getFinancialCategory()));
    }
  }

  public record PendingIncomeResponse(
      Long id,
      BigDecimal amount,
      String description,
      LocalDate date,
      String source,
      String sourceId,
      ExpenseSource sourceType,
      PendingIncomeStatus status,
      LocalDateTime createdAt,
      PropertyRefResponse property,
      PayerRefResponse payer,
      FinancialActivityResponse activity,
      FinancialCategoryResponse financialCategory,
      boolean classificationAmbiguous) {
    public static PendingIncomeResponse from(PendingIncome pending) {
      if (pending == null) {
        return null;
      }
      return new PendingIncomeResponse(
          pending.getId(),
          pending.getAmount(),
          pending.getDescription(),
          pending.getDate(),
          pending.getSource(),
          pending.getSourceId(),
          pending.getSourceType(),
          pending.getStatus(),
          pending.getCreatedAt(),
          PropertyRefResponse.from(pending.getProperty()),
          PayerRefResponse.from(pending.getPayer()),
          FinancialActivityResponse.from(pending.getActivity()),
          FinancialCategoryResponse.from(pending.getFinancialCategory()),
          pending.isClassificationAmbiguous());
    }
  }

  public record VenmoIncomeImportResponse(
      int totalRows,
      int importedRows,
      int skippedSenderRows,
      int skippedOutgoingRows,
      int skippedDuplicateRows,
      int skippedInvalidRows,
      String senderFilter,
      String propertyName,
      String activityName) {}

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
      String counterpartyName,
      FinancialActivityResponse activity,
      FinancialCategoryResponse financialCategory,
      boolean classificationAmbiguous,
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
          pendingExpense.getPayerName(),
          FinancialActivityResponse.from(pendingExpense.getActivity()),
          FinancialCategoryResponse.from(pendingExpense.getFinancialCategory()),
          pendingExpense.isClassificationAmbiguous(),
          pendingExpense.getErrorMessage(),
          pendingExpense.getCreatedAt());
    }
  }
}
