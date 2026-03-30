package com.bookie.controller;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseCategoryDto;
import com.bookie.service.ExpenseService;
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

  @GetMapping
  public List<Expense> getAll() {
    return expenseService.findAll();
  }

  @GetMapping("/{id}")
  public Expense getById(@PathVariable Long id) {
    return expenseService.findById(id);
  }

  @PostMapping
  public Expense create(@RequestBody Expense expense) {
    return expenseService.save(expense);
  }

  @PutMapping("/{id}")
  public Expense update(@PathVariable Long id, @RequestBody Expense expense) {
    return expenseService.update(id, expense);
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
