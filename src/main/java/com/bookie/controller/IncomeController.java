package com.bookie.controller;

import com.bookie.model.Income;
import com.bookie.service.IncomeService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incomes")
@RequiredArgsConstructor
public class IncomeController {

  private final IncomeService incomeService;

  @GetMapping
  public List<Income> getAll() {
    return incomeService.findAll();
  }

  @GetMapping("/{id}")
  public Income getById(@PathVariable Long id) {
    return incomeService.findById(id);
  }

  @PostMapping
  public Income create(@RequestBody Income income) {
    return incomeService.save(income);
  }

  @PutMapping("/{id}")
  public Income update(@PathVariable Long id, @RequestBody Income income) {
    return incomeService.update(id, income);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    incomeService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/total")
  public Map<String, BigDecimal> getTotal() {
    return Map.of("total", incomeService.getTotalIncome());
  }
}
