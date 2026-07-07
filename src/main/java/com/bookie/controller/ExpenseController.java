package com.bookie.controller;

import com.bookie.model.CreateExpenseRequest;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseCategoryDto;
import com.bookie.model.UpdateExpenseRequest;
import com.bookie.service.ExpenseService;
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
    return ApiResponses.ExpenseResponse.from(expenseService.create(req));
  }

  @PutMapping("/{id}")
  public ApiResponses.ExpenseResponse update(
      @PathVariable Long id, @Valid @RequestBody UpdateExpenseRequest req) {
    return ApiResponses.ExpenseResponse.from(expenseService.update(id, req));
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
