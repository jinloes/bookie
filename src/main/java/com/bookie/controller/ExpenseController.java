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
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

  private final ExpenseService expenseService;
  private final ReceiptService receiptService;
  private final PropertyService propertyService;
  private final PayerService payerService;

  @GetMapping
  public List<ApiResponses.ExpenseResponse> getAll() {
    return expenseService.findAll().stream().map(ApiResponses.ExpenseResponse::from).toList();
  }

  @GetMapping("/{id}")
  public ApiResponses.ExpenseResponse getById(@PathVariable Long id) {
    return ApiResponses.ExpenseResponse.from(expenseService.findById(id));
  }

  @PostMapping
  public ApiResponses.ExpenseResponse create(@Valid @RequestBody CreateExpenseRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Payer payer = req.payerId() != null ? payerService.findById(req.payerId()) : null;
    var expense =
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
      if (saved.getDate() == null) {
        throw new IllegalArgumentException("Receipt-sourced expenses require a non-null date");
      }
      receiptService.moveTaxesFolder(saved.getReceiptOneDriveId(), saved.getDate().getYear());
    }
    return ApiResponses.ExpenseResponse.from(saved);
  }

  @PutMapping("/{id}")
  public ApiResponses.ExpenseResponse update(
      @PathVariable Long id, @Valid @RequestBody UpdateExpenseRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Payer payer = req.payerId() != null ? payerService.findById(req.payerId()) : null;
    var updated =
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
    return ApiResponses.ExpenseResponse.from(expenseService.update(id, updated));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    expenseService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/total")
  public ApiResponses.TotalAmountResponse getTotal() {
    return new ApiResponses.TotalAmountResponse(expenseService.getTotalExpenses());
  }

  @GetMapping("/categories")
  public List<ExpenseCategoryDto> getCategories() {
    return Arrays.stream(ExpenseCategory.values()).map(ExpenseCategoryDto::from).toList();
  }
}
