package com.bookie.service;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.ConfirmedClassification;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.domain.Property;
import com.bookie.ledger.application.LedgerReadMode;
import com.bookie.ledger.compatibility.LegacyLedgerReadAdapter;
import com.bookie.ledger.compatibility.LegacyLedgerSynchronizer;
import com.bookie.model.CreateExpenseRequest;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialCategory;
import com.bookie.model.TransactionDirection;
import com.bookie.model.UpdateExpenseRequest;
import com.bookie.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExpenseService {

  private final ExpenseRepository expenseRepository;
  private final ClassificationHistory classificationHistory;
  private final CounterpartyCatalog counterpartyCatalog;
  private final ReceiptService receiptService;
  private final ActivityCatalog activityCatalog;
  private final FinancialCategoryService financialCategoryService;
  private final LegacyLedgerSynchronizer ledgerSynchronizer;
  private final LegacyLedgerReadAdapter ledgerReadAdapter;

  @Value("${bookie.ledger.read-mode:UNIFIED}")
  private LedgerReadMode ledgerReadMode = LedgerReadMode.UNIFIED;

  public List<Expense> findAll() {
    if (ledgerReadMode == LedgerReadMode.UNIFIED) {
      return ledgerReadAdapter.findAllExpenses();
    }
    List<Expense> expenses = expenseRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
    if (ledgerReadMode == LedgerReadMode.COMPARE) {
      ledgerReadAdapter.assertExpenseParity(expenses);
    }
    return expenses;
  }

  public Expense findById(Long id) {
    if (ledgerReadMode == LedgerReadMode.UNIFIED) {
      return ledgerReadAdapter.findExpenseById(id);
    }
    Expense expense =
        expenseRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found: " + id));
    if (ledgerReadMode == LedgerReadMode.COMPARE) {
      ledgerReadAdapter.assertExpenseParity(expense);
    }
    return expense;
  }

  /** Returns the expense linked to the given external source ID, if any already exists. */
  public Optional<Expense> findBySourceId(String sourceId) {
    return expenseRepository.findBySourceId(sourceId);
  }

  @Transactional
  public Expense create(CreateExpenseRequest req) {
    FinancialActivity activity =
        activityCatalog.resolveForTransaction(req.activityId(), req.propertyId());
    Property property = activity.getProperty();
    Counterparty payer = req.payerId() != null ? counterpartyCatalog.findById(req.payerId()) : null;
    FinancialCategory financialCategory =
        financialCategoryService.resolve(
            req.categoryId(),
            req.category() == null ? null : req.category().name(),
            TransactionDirection.EXPENSE,
            activity,
            req.date());
    Expense expense =
        Expense.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .category(financialCategoryService.toLegacyExpenseCategory(financialCategory))
            .financialCategory(financialCategory)
            .property(property)
            .payer(payer)
            .activity(activity)
            .receiptOneDriveId(req.receiptOneDriveId())
            .receiptFileName(req.receiptFileName())
            .sourceType(req.sourceType())
            .build();
    Expense saved = save(expense);
    if (saved.getSourceType() == ExpenseSource.RECEIPT && saved.getReceiptOneDriveId() != null) {
      receiptService.moveTaxesFolder(saved.getReceiptOneDriveId(), saved.getDate().getYear());
    }
    return saved;
  }

  @Transactional
  public Expense update(Long id, UpdateExpenseRequest req) {
    Expense existing = findById(id);
    FinancialActivity activity =
        req.activityId() == null && req.propertyId() == null && existing.getActivity() != null
            ? activityCatalog.findActiveById(existing.getActivity().getId())
            : activityCatalog.resolveForTransaction(req.activityId(), req.propertyId());
    Property property = activity.getProperty();
    Counterparty payer = req.payerId() != null ? counterpartyCatalog.findById(req.payerId()) : null;
    FinancialCategory financialCategory =
        req.categoryId() != null || req.category() != null
            ? financialCategoryService.resolve(
                req.categoryId(),
                req.category() == null ? null : req.category().name(),
                TransactionDirection.EXPENSE,
                activity,
                req.date())
            : compatibleOrDefault(existing.getFinancialCategory(), activity, req.date());
    Expense updated =
        Expense.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .category(financialCategoryService.toLegacyExpenseCategory(financialCategory))
            .financialCategory(financialCategory)
            .property(property)
            .payer(payer)
            .activity(activity)
            .receiptOneDriveId(req.receiptOneDriveId())
            .receiptFileName(req.receiptFileName())
            .build();
    return update(id, updated);
  }

  @Transactional
  public Expense save(Expense expense) {
    Expense saved = expenseRepository.save(expense);
    ledgerSynchronizer.synchronize(saved);
    classificationHistory.record(classification(saved));
    return saved;
  }

  @Transactional
  public Expense update(Long id, Expense updated) {
    Expense existing = findById(id);
    existing.setAmount(updated.getAmount());
    existing.setDescription(updated.getDescription());
    existing.setDate(updated.getDate());
    existing.setCategory(updated.getCategory());
    existing.setProperty(updated.getProperty());
    existing.setPayer(updated.getPayer());
    existing.setActivity(updated.getActivity());
    existing.setFinancialCategory(updated.getFinancialCategory());
    existing.setReceiptOneDriveId(updated.getReceiptOneDriveId());
    existing.setReceiptFileName(updated.getReceiptFileName());
    Expense saved = expenseRepository.save(existing);
    ledgerSynchronizer.synchronize(saved);
    classificationHistory.record(classification(saved));
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    ledgerSynchronizer.tombstoneExpense(id);
    expenseRepository.deleteById(id);
    expenseRepository.flush();
  }

  @Transactional
  public void updateSourceId(Long id, String newSourceId) {
    expenseRepository
        .findById(id)
        .ifPresent(
            expense -> {
              expense.setSourceId(newSourceId);
              Expense saved = expenseRepository.save(expense);
              ledgerSynchronizer.synchronize(saved);
            });
  }

  public BigDecimal getTotalExpenses() {
    if (ledgerReadMode == LedgerReadMode.UNIFIED) {
      return ledgerReadAdapter.getTotalExpenses();
    }
    BigDecimal total = expenseRepository.getTotalExpenses();
    if (ledgerReadMode == LedgerReadMode.COMPARE) {
      ledgerReadAdapter.assertExpenseParity(expenseRepository.findAll());
    }
    return total;
  }

  private FinancialCategory compatibleOrDefault(
      FinancialCategory current, FinancialActivity activity, LocalDate effectiveOn) {
    if (financialCategoryService.isCompatible(
        current, activity, TransactionDirection.EXPENSE, effectiveOn)) {
      return current;
    }
    return financialCategoryService.defaultFor(activity, TransactionDirection.EXPENSE, effectiveOn);
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
}
