package com.bookie.ledger.compatibility;

import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.ledger.application.LedgerParityException;
import com.bookie.ledger.application.LedgerReferenceResolver;
import com.bookie.ledger.application.LedgerTransactionService;
import com.bookie.ledger.application.ResolvedLegacyReferences;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionMap;
import com.bookie.ledger.domain.LegacyTransactionTable;
import com.bookie.ledger.domain.TransactionAttachment;
import com.bookie.ledger.domain.TransactionImportReference;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Income;
import com.bookie.model.TransactionDirection;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
class JpaLegacyLedgerReadAdapter implements LegacyLedgerReadAdapter {

  private final LedgerTransactionService ledgerTransactionService;
  private final LedgerReferenceResolver referenceResolver;
  private final EntityManager entityManager;

  @Override
  @Transactional(readOnly = true)
  public List<Income> findAllIncomes() {
    return ledgerTransactionService.findAll(TransactionDirection.INCOME).stream()
        .map(this::toIncome)
        .sorted(Comparator.comparing(Income::getDate).reversed().thenComparing(Income::getId))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Income findIncomeById(Long id) {
    return ledgerTransactionService
        .findByLegacyKey(new LegacyTransactionKey(LegacyTransactionTable.INCOMES, id))
        .map(this::toIncome)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income not found: " + id));
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal getTotalIncome() {
    return sum(ledgerTransactionService.findAll(TransactionDirection.INCOME));
  }

  @Override
  @Transactional(readOnly = true)
  public void assertIncomeParity(List<Income> legacyIncomes) {
    Map<Long, Income> unifiedById = byIncomeId(findAllIncomes());
    if (legacyIncomes.size() != unifiedById.size()) {
      throw parityFailure("Income counts differ");
    }
    for (Income legacy : legacyIncomes) {
      Income unified = unifiedById.get(legacy.getId());
      if (unified == null || !sameIncome(legacy, unified)) {
        throw parityFailure("Income differs for legacy ID " + legacy.getId());
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void assertIncomeParity(Income legacyIncome) {
    Income unified = findIncomeById(legacyIncome.getId());
    if (!sameIncome(legacyIncome, unified)) {
      throw parityFailure("Income differs for legacy ID " + legacyIncome.getId());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<Expense> findAllExpenses() {
    return ledgerTransactionService.findAll(TransactionDirection.EXPENSE).stream()
        .map(this::toExpense)
        .sorted(Comparator.comparing(Expense::getDate).reversed().thenComparing(Expense::getId))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Expense findExpenseById(Long id) {
    return ledgerTransactionService
        .findByLegacyKey(new LegacyTransactionKey(LegacyTransactionTable.EXPENSES, id))
        .map(this::toExpense)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found: " + id));
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal getTotalExpenses() {
    return sum(ledgerTransactionService.findAll(TransactionDirection.EXPENSE));
  }

  @Override
  @Transactional(readOnly = true)
  public void assertExpenseParity(List<Expense> legacyExpenses) {
    Map<Long, Expense> unifiedById = byExpenseId(findAllExpenses());
    if (legacyExpenses.size() != unifiedById.size()) {
      throw parityFailure("Expense counts differ");
    }

    for (Expense legacy : legacyExpenses) {
      Expense unified = unifiedById.get(legacy.getId());
      if (unified == null || !sameExpense(legacy, unified)) {
        throw parityFailure("Expense differs for legacy ID " + legacy.getId());
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void assertExpenseParity(Expense legacyExpense) {
    Expense unified = findExpenseById(legacyExpense.getId());
    if (!sameExpense(legacyExpense, unified)) {
      throw parityFailure("Expense differs for legacy ID " + legacyExpense.getId());
    }
  }

  private Income toIncome(FinancialTransaction transaction) {
    LegacyTransactionMap map = requiredMap(transaction, LegacyTransactionTable.INCOMES);
    ResolvedLegacyReferences references = legacyReferences(transaction);
    TransactionImportReference source = primarySource(transaction);
    TransactionAttachment attachment = primaryAttachment(transaction);
    return Income.builder()
        .id(map.getKey().getId())
        .amount(transaction.getAmount())
        .description(transaction.getDescription())
        .date(transaction.getDate())
        .source(source == null ? null : source.getSourceLabel())
        .sourceId(source == null ? null : source.getExternalId())
        .sourceType(sourceType(source))
        .receiptOneDriveId(attachment == null ? null : attachment.getExternalId())
        .receiptFileName(attachment == null ? null : attachment.getFileName())
        .property(transaction.getActivity().getProperty())
        .payer(counterparty(references.legacyCounterpartyId()))
        .activity(transaction.getActivity())
        .financialCategory(requiredCategory(references.legacyCategoryId()))
        .build();
  }

  private Expense toExpense(FinancialTransaction transaction) {
    LegacyTransactionMap map = requiredMap(transaction, LegacyTransactionTable.EXPENSES);
    ResolvedLegacyReferences references = legacyReferences(transaction);
    FinancialCategory category = requiredCategory(references.legacyCategoryId());
    TransactionImportReference source = primarySource(transaction);
    TransactionAttachment attachment = primaryAttachment(transaction);
    return Expense.builder()
        .id(map.getKey().getId())
        .amount(transaction.getAmount())
        .description(transaction.getDescription())
        .date(transaction.getDate())
        .category(map.getLegacyExpenseCategory())
        .sourceId(source == null ? null : source.getExternalId())
        .sourceType(sourceType(source))
        .receiptOneDriveId(attachment == null ? null : attachment.getExternalId())
        .receiptFileName(attachment == null ? null : attachment.getFileName())
        .property(transaction.getActivity().getProperty())
        .payer(counterparty(references.legacyCounterpartyId()))
        .activity(transaction.getActivity())
        .financialCategory(category)
        .build();
  }

  private ResolvedLegacyReferences legacyReferences(FinancialTransaction transaction) {
    return referenceResolver.resolveForUnified(
        transaction.getActivity().getId(),
        transaction.getNeutralCategory().getId(),
        transaction.getCounterpartyId(),
        transaction.getDate());
  }

  private LegacyTransactionMap requiredMap(
      FinancialTransaction transaction, LegacyTransactionTable expectedTable) {
    LegacyTransactionMap map = ledgerTransactionService.findMapByTransactionId(transaction.getId());
    if (map.getKey().getTable() != expectedTable) {
      throw parityFailure(
          "Transaction direction and legacy table differ for transaction " + transaction.getId());
    }
    return map;
  }

  private FinancialCategory requiredCategory(Long id) {
    FinancialCategory category = entityManager.find(FinancialCategory.class, id);
    if (category == null) {
      throw parityFailure("Legacy category is missing: " + id);
    }
    return category;
  }

  private Counterparty counterparty(Long id) {
    if (id == null) {
      return null;
    }
    Counterparty counterparty = entityManager.find(Counterparty.class, id);
    if (counterparty == null) {
      throw parityFailure("Legacy counterparty is missing: " + id);
    }
    return counterparty;
  }

  private TransactionImportReference primarySource(FinancialTransaction transaction) {
    return transaction.getImportReferences().stream()
        .min(Comparator.comparing(value -> value.getId() == null ? 0L : value.getId()))
        .orElse(null);
  }

  private TransactionAttachment primaryAttachment(FinancialTransaction transaction) {
    return transaction.getAttachments().stream()
        .min(Comparator.comparing(value -> value.getId() == null ? 0L : value.getId()))
        .orElse(null);
  }

  private ExpenseSource sourceType(TransactionImportReference source) {
    if (source == null || source.getOrigin() == null) {
      return null;
    }
    try {
      return ExpenseSource.valueOf(source.getOrigin());
    } catch (IllegalArgumentException exception) {
      throw parityFailure("Legacy source type is not representable: " + source.getOrigin());
    }
  }

  private BigDecimal sum(List<FinancialTransaction> transactions) {
    return transactions.stream()
        .map(FinancialTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Map<Long, Income> byIncomeId(List<Income> incomes) {
    Map<Long, Income> result = new LinkedHashMap<>();
    incomes.forEach(income -> result.put(income.getId(), income));
    return result;
  }

  private Map<Long, Expense> byExpenseId(List<Expense> expenses) {
    Map<Long, Expense> result = new LinkedHashMap<>();
    expenses.forEach(expense -> result.put(expense.getId(), expense));
    return result;
  }

  private boolean sameIncome(Income legacy, Income unified) {
    return sameAmount(legacy.getAmount(), unified.getAmount())
        && Objects.equals(legacy.getDescription(), unified.getDescription())
        && Objects.equals(legacy.getDate(), unified.getDate())
        && Objects.equals(legacy.getSource(), unified.getSource())
        && Objects.equals(legacy.getSourceId(), unified.getSourceId())
        && legacy.getSourceType() == unified.getSourceType()
        && Objects.equals(id(legacy.getProperty()), id(unified.getProperty()))
        && Objects.equals(id(legacy.getPayer()), id(unified.getPayer()))
        && Objects.equals(id(legacy.getActivity()), id(unified.getActivity()))
        && Objects.equals(id(legacy.getFinancialCategory()), id(unified.getFinancialCategory()))
        && Objects.equals(legacy.getReceiptOneDriveId(), unified.getReceiptOneDriveId())
        && Objects.equals(legacy.getReceiptFileName(), unified.getReceiptFileName());
  }

  private boolean sameExpense(Expense legacy, Expense unified) {
    return sameAmount(legacy.getAmount(), unified.getAmount())
        && Objects.equals(legacy.getDescription(), unified.getDescription())
        && Objects.equals(legacy.getDate(), unified.getDate())
        && legacy.getCategory() == unified.getCategory()
        && Objects.equals(legacy.getSourceId(), unified.getSourceId())
        && legacy.getSourceType() == unified.getSourceType()
        && Objects.equals(id(legacy.getProperty()), id(unified.getProperty()))
        && Objects.equals(id(legacy.getPayer()), id(unified.getPayer()))
        && Objects.equals(id(legacy.getActivity()), id(unified.getActivity()))
        && Objects.equals(id(legacy.getFinancialCategory()), id(unified.getFinancialCategory()))
        && Objects.equals(legacy.getReceiptOneDriveId(), unified.getReceiptOneDriveId())
        && Objects.equals(legacy.getReceiptFileName(), unified.getReceiptFileName());
  }

  private boolean sameAmount(BigDecimal first, BigDecimal second) {
    return first == null ? second == null : second != null && first.compareTo(second) == 0;
  }

  private Long id(Object entity) {
    if (entity == null) {
      return null;
    }
    if (entity instanceof com.bookie.catalog.property.domain.Property property) {
      return property.getId();
    }
    if (entity instanceof Counterparty counterparty) {
      return counterparty.getId();
    }
    if (entity instanceof com.bookie.catalog.activity.domain.FinancialActivity activity) {
      return activity.getId();
    }
    if (entity instanceof FinancialCategory category) {
      return category.getId();
    }
    throw new IllegalArgumentException("Unsupported legacy reference: " + entity.getClass());
  }

  private LedgerParityException parityFailure(String message) {
    return new LedgerParityException(message);
  }
}
