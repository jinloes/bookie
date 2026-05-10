package com.bookie.controller;

import com.bookie.model.CreateExpenseRequest;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseCategoryDto;
import com.bookie.model.ExpenseSource;
import com.bookie.model.Payer;
import com.bookie.model.Property;
import com.bookie.model.UpdateExpenseRequest;
import com.bookie.service.ExpenseService;
import com.bookie.service.PayerService;
import com.bookie.service.PropertyService;
import com.bookie.service.ReceiptService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

  private final ExpenseService expenseService;
  private final ReceiptService receiptService;
  private final PropertyService propertyService;
  private final PayerService payerService;

  @GetMapping
  public List<Expense> getAll() {
    return expenseService.findAll();
  }

  @GetMapping("/{id}")
  public Expense getById(@PathVariable Long id) {
    return expenseService.findById(id);
  }

  @PostMapping
  public Expense create(@RequestBody CreateExpenseRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Payer payer = req.payerId() != null ? payerService.findById(req.payerId()) : null;
    Expense expense =
        Expense.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .category(req.category())
            .property(property)
            .payer(payer)
            .receiptOneDriveId(req.receiptOneDriveId())
            .receiptFileName(req.receiptFileName())
            .sourceType(req.sourceType())
            .build();
    Expense saved = expenseService.save(expense);
    if (saved.getSourceType() == ExpenseSource.RECEIPT && saved.getReceiptOneDriveId() != null) {
      receiptService.moveTaxesFolder(saved.getReceiptOneDriveId(), saved.getDate().getYear());
    }
    return saved;
  }

  @PutMapping("/{id}")
  public Expense update(@PathVariable Long id, @RequestBody UpdateExpenseRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Payer payer = req.payerId() != null ? payerService.findById(req.payerId()) : null;
    Expense updated =
        Expense.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .category(req.category())
            .property(property)
            .payer(payer)
            .receiptOneDriveId(req.receiptOneDriveId())
            .receiptFileName(req.receiptFileName())
            .build();
    return expenseService.update(id, updated);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    expenseService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/total")
  public Map<String, BigDecimal> getTotal() {
    return Map.of("total", expenseService.getTotalExpenses());
  }

  @GetMapping("/categories")
  public List<ExpenseCategoryDto> getCategories() {
    return Arrays.stream(ExpenseCategory.values()).map(ExpenseCategoryDto::from).toList();
  }
}
