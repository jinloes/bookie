package com.bookie.service;

import com.bookie.model.CreateExpenseRequest;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FinancialActivity;
import com.bookie.model.FinancialCategory;
import com.bookie.model.Payer;
import com.bookie.model.Property;
import com.bookie.model.TransactionDirection;
import com.bookie.model.UpdateExpenseRequest;
import com.bookie.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExpenseService {

  private final ExpenseRepository expenseRepository;
  private final PropertyHistoryService propertyHistoryService;
  private final PropertyService propertyService;
  private final PayerService payerService;
  private final ReceiptService receiptService;
  private final FinancialActivityService financialActivityService;
  private final FinancialCategoryService financialCategoryService;

  public List<Expense> findAll() {
    return expenseRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
  }

  public Expense findById(Long id) {
    return expenseRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found: " + id));
  }

  /** Returns the expense linked to the given external source ID, if any already exists. */
  public Optional<Expense> findBySourceId(String sourceId) {
    return expenseRepository.findBySourceId(sourceId);
  }

  @Transactional
  public Expense create(CreateExpenseRequest req) {
    FinancialActivity activity =
        financialActivityService.resolveForTransaction(req.activityId(), req.propertyId());
    Property property = activity.getProperty();
    Payer payer = req.payerId() != null ? payerService.findById(req.payerId()) : null;
    FinancialCategory financialCategory =
        financialCategoryService.resolve(
            req.categoryId(),
            req.category() == null ? null : req.category().name(),
            TransactionDirection.EXPENSE,
            activity);
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
            ? financialActivityService.findActiveById(existing.getActivity().getId())
            : financialActivityService.resolveForTransaction(req.activityId(), req.propertyId());
    Property property = activity.getProperty();
    Payer payer = req.payerId() != null ? payerService.findById(req.payerId()) : null;
    FinancialCategory financialCategory =
        req.categoryId() != null || req.category() != null
            ? financialCategoryService.resolve(
                req.categoryId(),
                req.category() == null ? null : req.category().name(),
                TransactionDirection.EXPENSE,
                activity)
            : compatibleOrDefault(existing.getFinancialCategory(), activity);
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
    propertyHistoryService.record(saved);
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
    propertyHistoryService.record(saved);
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    expenseRepository.deleteById(id);
  }

  @Transactional
  public void updateSourceId(Long id, String newSourceId) {
    expenseRepository
        .findById(id)
        .ifPresent(
            expense -> {
              expense.setSourceId(newSourceId);
              expenseRepository.save(expense);
            });
  }

  public BigDecimal getTotalExpenses() {
    return expenseRepository.getTotalExpenses();
  }

  private FinancialCategory compatibleOrDefault(
      FinancialCategory current, FinancialActivity activity) {
    if (current != null
        && current.isActive()
        && current.getDirection() == TransactionDirection.EXPENSE
        && current.getTaxTreatment() == activity.getTaxTreatment()) {
      return current;
    }
    return financialCategoryService.defaultFor(activity, TransactionDirection.EXPENSE);
  }
}
