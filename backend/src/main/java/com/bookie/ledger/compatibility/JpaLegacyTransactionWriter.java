package com.bookie.ledger.compatibility;

import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.ConfirmedClassification;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.ledger.application.LedgerTransactionInput;
import com.bookie.ledger.application.LegacyTransactionSnapshot;
import com.bookie.ledger.application.LegacyTransactionWriter;
import com.bookie.ledger.application.ResolvedLegacyReferences;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionTable;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Income;
import com.bookie.model.TransactionDirection;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.FinancialCategoryRepository;
import com.bookie.repository.IncomeRepository;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
class JpaLegacyTransactionWriter implements LegacyTransactionWriter {

  private final IncomeRepository incomeRepository;
  private final ExpenseRepository expenseRepository;
  private final FinancialCategoryRepository categoryRepository;
  private final ClassificationHistory classificationHistory;
  private final EntityManager entityManager;

  @Override
  public LegacyTransactionSnapshot create(
      LedgerTransactionInput input, ResolvedLegacyReferences references) {
    return input.getDirection() == TransactionDirection.INCOME
        ? createIncome(input, references)
        : createExpense(input, references);
  }

  @Override
  public LegacyTransactionSnapshot update(
      LegacyTransactionKey key, LedgerTransactionInput input, ResolvedLegacyReferences references) {
    if (key.getTable() == LegacyTransactionTable.INCOMES) {
      if (input.getDirection() != TransactionDirection.INCOME) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Transaction direction cannot be changed");
      }
      Income income = incomeRepository.findById(key.getId()).orElseThrow(() -> parityFailure(key));
      applyIncome(income, input, references);
      Income saved = incomeRepository.saveAndFlush(income);
      classificationHistory.record(classification(saved));
      return snapshot(saved, input);
    }
    if (input.getDirection() != TransactionDirection.EXPENSE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Transaction direction cannot be changed");
    }
    Expense expense = expenseRepository.findById(key.getId()).orElseThrow(() -> parityFailure(key));
    applyExpense(expense, input, references);
    Expense saved = expenseRepository.saveAndFlush(expense);
    classificationHistory.record(classification(saved));
    return snapshot(saved, input);
  }

  @Override
  public void delete(LegacyTransactionKey key) {
    if (key.getTable() == LegacyTransactionTable.INCOMES) {
      if (!incomeRepository.existsById(key.getId())) {
        throw parityFailure(key);
      }
      incomeRepository.deleteById(key.getId());
      incomeRepository.flush();
      return;
    }
    if (!expenseRepository.existsById(key.getId())) {
      throw parityFailure(key);
    }
    expenseRepository.deleteById(key.getId());
    expenseRepository.flush();
  }

  private LegacyTransactionSnapshot createIncome(
      LedgerTransactionInput input, ResolvedLegacyReferences references) {
    Income income = Income.builder().build();
    applyIncome(income, input, references);
    Income saved = incomeRepository.saveAndFlush(income);
    classificationHistory.record(classification(saved));
    return snapshot(saved, input);
  }

  private LegacyTransactionSnapshot createExpense(
      LedgerTransactionInput input, ResolvedLegacyReferences references) {
    Expense expense = Expense.builder().build();
    applyExpense(expense, input, references);
    Expense saved = expenseRepository.saveAndFlush(expense);
    classificationHistory.record(classification(saved));
    return snapshot(saved, input);
  }

  private void applyIncome(
      Income income, LedgerTransactionInput input, ResolvedLegacyReferences references) {
    income.setAmount(input.getAmount());
    income.setDescription(input.getDescription());
    income.setDate(input.getDate());
    income.setSource(input.getSourceLabel());
    income.setSourceId(input.getExternalId());
    income.setSourceType(sourceType(input.getOrigin()));
    income.setActivity(references.activity());
    income.setProperty(references.activity().getProperty());
    income.setFinancialCategory(requiredCategory(references.legacyCategoryId()));
    income.setPayer(counterparty(references.legacyCounterpartyId()));
    income.setReceiptOneDriveId(input.getAttachmentExternalId());
    income.setReceiptFileName(input.getAttachmentFileName());
  }

  private void applyExpense(
      Expense expense, LedgerTransactionInput input, ResolvedLegacyReferences references) {
    FinancialCategory category = requiredCategory(references.legacyCategoryId());
    expense.setAmount(input.getAmount());
    expense.setDescription(input.getDescription());
    expense.setDate(input.getDate());
    expense.setCategory(legacyExpenseCategory(category));
    expense.setSourceId(input.getExternalId());
    expense.setSourceType(sourceType(input.getOrigin()));
    expense.setActivity(references.activity());
    expense.setProperty(references.activity().getProperty());
    expense.setFinancialCategory(category);
    expense.setPayer(counterparty(references.legacyCounterpartyId()));
    expense.setReceiptOneDriveId(input.getAttachmentExternalId());
    expense.setReceiptFileName(input.getAttachmentFileName());
  }

  private LegacyTransactionSnapshot snapshot(Income income, LedgerTransactionInput input) {
    return LegacyTransactionSnapshot.builder()
        .key(new LegacyTransactionKey(LegacyTransactionTable.INCOMES, income.getId()))
        .amount(income.getAmount())
        .direction(TransactionDirection.INCOME)
        .date(income.getDate())
        .description(income.getDescription())
        .activityId(income.getActivity().getId())
        .legacyCategoryId(income.getFinancialCategory().getId())
        .legacyCounterpartyId(income.getPayer() == null ? null : income.getPayer().getId())
        .origin(income.getSourceType() == null ? null : income.getSourceType().name())
        .externalId(income.getSourceId())
        .sourceLabel(income.getSource())
        .attachmentStorageProvider(
            income.getReceiptOneDriveId() == null && income.getReceiptFileName() == null
                ? null
                : "ONEDRIVE")
        .attachmentExternalId(income.getReceiptOneDriveId())
        .attachmentFileName(income.getReceiptFileName())
        .attachmentSha256(input.getAttachmentSha256())
        .normalizedMetadataAuthoritative(true)
        .build();
  }

  private LegacyTransactionSnapshot snapshot(Expense expense, LedgerTransactionInput input) {
    return LegacyTransactionSnapshot.builder()
        .key(new LegacyTransactionKey(LegacyTransactionTable.EXPENSES, expense.getId()))
        .amount(expense.getAmount())
        .direction(TransactionDirection.EXPENSE)
        .date(expense.getDate())
        .description(expense.getDescription())
        .activityId(expense.getActivity().getId())
        .legacyCategoryId(expense.getFinancialCategory().getId())
        .legacyCounterpartyId(expense.getPayer() == null ? null : expense.getPayer().getId())
        .legacyExpenseCategory(expense.getCategory())
        .origin(expense.getSourceType() == null ? null : expense.getSourceType().name())
        .externalId(expense.getSourceId())
        .attachmentStorageProvider(
            expense.getReceiptOneDriveId() == null && expense.getReceiptFileName() == null
                ? null
                : "ONEDRIVE")
        .attachmentExternalId(expense.getReceiptOneDriveId())
        .attachmentFileName(expense.getReceiptFileName())
        .attachmentSha256(input.getAttachmentSha256())
        .normalizedMetadataAuthoritative(true)
        .build();
  }

  private FinancialCategory requiredCategory(Long id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Legacy category is missing during compatibility write: " + id));
  }

  private Counterparty counterparty(Long legacyCounterpartyId) {
    return legacyCounterpartyId == null
        ? null
        : entityManager.getReference(Counterparty.class, legacyCounterpartyId);
  }

  private ExpenseSource sourceType(String origin) {
    if (origin == null) {
      return null;
    }
    try {
      return ExpenseSource.valueOf(origin.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Source origin is not representable during compatibility mode: " + origin);
    }
  }

  private ExpenseCategory legacyExpenseCategory(FinancialCategory category) {
    try {
      return ExpenseCategory.valueOf(category.getKey());
    } catch (IllegalArgumentException ignored) {
      return ExpenseCategory.OTHER;
    }
  }

  private ConfirmedClassification classification(Income income) {
    return ConfirmedClassification.builder()
        .kind(ConfirmedClassification.Kind.INCOME)
        .sourceId(income.getSourceId())
        .activity(income.getActivity())
        .financialCategoryId(
            income.getFinancialCategory() == null ? null : income.getFinancialCategory().getId())
        .property(income.getProperty())
        .counterparty(income.getPayer())
        .build();
  }

  private ConfirmedClassification classification(Expense expense) {
    return ConfirmedClassification.builder()
        .kind(ConfirmedClassification.Kind.EXPENSE)
        .sourceId(expense.getSourceId())
        .activity(expense.getActivity())
        .financialCategoryId(
            expense.getFinancialCategory() == null ? null : expense.getFinancialCategory().getId())
        .property(expense.getProperty())
        .counterparty(expense.getPayer())
        .legacyExpenseCategory(expense.getCategory() == null ? null : expense.getCategory().name())
        .build();
  }

  private IllegalStateException parityFailure(LegacyTransactionKey key) {
    return new IllegalStateException(
        "Legacy transaction is missing during compatibility write: "
            + key.getTable()
            + "/"
            + key.getId());
  }
}
