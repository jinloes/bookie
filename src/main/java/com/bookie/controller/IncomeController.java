package com.bookie.controller;

import com.bookie.model.CreateIncomeRequest;
import com.bookie.model.Income;
import com.bookie.model.Property;
import com.bookie.model.UpdateIncomeRequest;
import com.bookie.service.IncomeService;
import com.bookie.service.PropertyService;
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
  private final PropertyService propertyService;

  @GetMapping
  public List<Income> getAll() {
    return incomeService.findAll();
  }

  @GetMapping("/{id}")
  public Income getById(@PathVariable Long id) {
    return incomeService.findById(id);
  }

  @PostMapping
  public Income create(@RequestBody CreateIncomeRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Income income =
        Income.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .source(req.source())
            .property(property)
            .sourceType(req.sourceType())
            .receiptOneDriveId(req.receiptOneDriveId())
            .receiptFileName(req.receiptFileName())
            .build();
    return incomeService.save(income);
  }

  @PutMapping("/{id}")
  public Income update(@PathVariable Long id, @RequestBody UpdateIncomeRequest req) {
    Property property =
        req.propertyId() != null ? propertyService.findById(req.propertyId()) : null;
    Income updated =
        Income.builder()
            .amount(req.amount())
            .description(req.description())
            .date(req.date())
            .source(req.source())
            .property(property)
            .build();
    return incomeService.update(id, updated);
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
